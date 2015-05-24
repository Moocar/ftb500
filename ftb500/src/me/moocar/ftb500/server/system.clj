(ns me.moocar.ftb500.server.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.server.clients :as clients]
            [me.moocar.ftb500.server.datomic-conn :as datomic-conn]
            [me.moocar.ftb500.server.router :as router]))

(defn construct [config]
  (let [tag :SERVER1
        system {:log-ch (async/chan (async/dropping-buffer 1000)
                                    (map #(assoc % :system tag)))
                :server-recv-ch (async/chan 1 (map #(assoc % :system tag)))
                :datomic-conn (datomic-conn/construct config)
                :clients (clients/new-clients)}]
    (component/system-using (assoc system :router (router/map->Router {}))
                            {:router (vec (keys system))})))
