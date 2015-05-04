(ns me.moocar.ftb500.server.datomic-conn
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defrecord DatomicConn [uri conn]
  component/Lifecycle
  (start [this]
    (if conn
      this
      (do (d/create-database uri)
          (assoc this :conn (d/connect uri)))))
  (stop [this]
    (if conn
      (do (d/release conn)
          (assoc this :conn nil))
      this)))

(defn construct
  [config]
  (let [uri (get-in config [:server :datomic :transactor :uri])]
    (assert uri)
    (map->DatomicConn {:uri uri})))
