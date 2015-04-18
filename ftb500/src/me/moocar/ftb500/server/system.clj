(ns me.moocar.ftb500.server.system
  (:require [clojure.core.async :as async]
            [me.moocar.ftb500.server.routes.system :as route-system]
            [me.moocar.ftb500.server :as server]
            [me.moocar.ftb500.server.clients :as clients]))

(defn new-system
  [config recv-ch]
  (let [tag :SERVER1]
    (merge
     (route-system/new-system)
     {:log-ch (async/chan 1 (map #(assoc % :system tag)))
      :server (server/new-server config recv-ch)
      :clients (clients/new-clients)})))
