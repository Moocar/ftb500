(ns me.moocar.ftb500.server.datomic-conn
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [datomic.api :as d])
  (:import (java.util.concurrent Executors)))

(defn read-edn [f]
  (with-open [reader (java.io.PushbackReader. (io/reader f))]
    (edn/read {:readers {'db/id datomic.db/id-literal
                         'db/fn datomic.function/construct}}
              reader)))

(defn tx-f
  [this]
  (let [{:keys [conn listener-executor]} this]
    (fn [[tx response-ch] result-ch]
      (let [ch (async/chan)
            f (d/transact-async conn tx)]
        (.addListener f #(async/close! ch) listener-executor)
        (async/take! ch (fn [_]
                          (do (async/put! response-ch @f)
                              (async/close! result-ch))))))))

(defrecord DatomicConn [;; Configuration
                        uri create-schema? create-database? reset-db?
                        ;; Dependencies
                        log-ch
                        ;; Runtime state
                        conn tx-ch listener-executor]
  component/Lifecycle
  (start [this]
    (if conn
      this
      (do
        (when reset-db?
          (async/put! log-ch {:deleting-db uri})
          (d/delete-database uri))

        (when create-database?
          (async/put! log-ch {:creating-db uri})
          (d/create-database uri))

        (let [conn (d/connect uri)
              tx-ch (async/chan)
              listener-executor (Executors/newFixedThreadPool 5)
              this (assoc this
                          :conn conn
                          :tx-ch tx-ch
                          :listener-executor listener-executor)]

          (async/pipeline-async 5 (async/chan (async/dropping-buffer 1))
                                (tx-f this)
                                tx-ch)

          (when create-schema?
            (async/put! log-ch {:creating-schema uri})
            (let [schema-resource (io/resource "datomic_schema.edn")]
              (assert schema-resource)
              (let [response-ch (async/chan)]
                (>!! tx-ch [(read-edn schema-resource) response-ch])
                (<!! response-ch))))


          this))))
  (stop [this]
    (if conn
      (do (d/release conn)
          (.shutdown listener-executor)
          (async/close! tx-ch)
          (assoc this :conn nil :tx-ch nil :listener-executor nil))
      this)))

(defn make-uri
  [config]
  (str (:sub-uri config) \/ (:db-name config)))

(defn construct
  [config]
  (let [datomic-config (get-in config [:server :datomic])]
    (assert (:sub-uri datomic-config))
    (assert (:db-name datomic-config))
    (component/using
      (map->DatomicConn (assoc datomic-config
                               :uri (make-uri datomic-config)))
      [:log-ch])))
