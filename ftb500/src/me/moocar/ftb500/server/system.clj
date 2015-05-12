(ns me.moocar.ftb500.server.system
  (:require [clojure.core.async :as async]
            [me.moocar.ftb500.server :as server]
            [me.moocar.ftb500.server.clients :as clients]
            [me.moocar.ftb500.server.datomic-conn :as datomic-conn]
            [me.moocar.ftb500.server.routes.system :as route-system]))

(defn construct [config]
  (let [tag :SERVER1]
    (merge
     (route-system/new-system)
     {:log-ch (async/chan (async/dropping-buffer 1000)
                          (map #(assoc % :system tag)))
      :me.moocar.ftb500/server (server/new-server config)
      :server-recv-ch (async/chan 1 (map #(assoc % :system tag)))
      :datomic-conn (datomic-conn/construct config)
      :clients (clients/new-clients)})))
