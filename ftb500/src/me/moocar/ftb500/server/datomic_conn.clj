(ns me.moocar.ftb500.server.datomic-conn
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defn read-edn [f]
  (with-open [reader (java.io.PushbackReader. (io/reader f))]
    (edn/read {:readers {'db/id datomic.db/id-literal
                         'db/fn datomic.function/construct}}
              reader)))

(defrecord DatomicConn [;; Configuration
                        uri create-schema? create-database? reset-db?
                        ;; Dependencies
                        log-ch
                        ;; Runtime state
                        conn]
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

        (let [conn (d/connect uri)]
          (when create-schema?
            (async/put! log-ch {:creating-schema uri})
            (let [schema-resource (io/resource "datomic_schema.edn")]
              (assert schema-resource)
              @(d/transact conn (read-edn schema-resource))))
          (assoc this :conn conn)))))
  (stop [this]
    (if conn
      (do (d/release conn)
          (assoc this :conn nil))
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
