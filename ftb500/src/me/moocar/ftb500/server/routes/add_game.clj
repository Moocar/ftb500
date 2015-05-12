(ns me.moocar.ftb500.server.routes.add-game
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [com.stuartsierra.component :as component]))

(defn start-read-loop [this ch]
  (go-loop []
    (when-let [request (<! ch)]
      ;; Success
      (>! (:send-ch request) (:msg request))
      (recur))))

(defrecord AddGame [server ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan 1)]
      (async/sub route-pub-ch :game/add ch)
      (start-read-loop this ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/unsub route-pub-ch :game/add ch)
      (assoc this :ch nil))))

(defn new-add-game []
  (component/using (map->AddGame {})
    {:server :me.moocar.ftb500/server
     :log-ch :log-ch}))
