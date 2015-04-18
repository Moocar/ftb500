(ns me.moocar.ftb500.server.routes.join-game
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.server.clients :as clients]))

(defn start-read-loop
  [this ch]
  (let [{:keys [clients]} this]
    (go-loop []
      (when-let [request (<! ch)]
        (let [{:keys [send-ch msg]} request
              game-id (get-in msg [:keys :game/id])]
          (clients/add-client clients game-id send-ch)
          (let [game-ch (clients/get-game-ch clients game-id)]
            (>! game-ch msg))
          (recur))))))

(defrecord JoinGame [server ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan 1)]
      (async/sub route-pub-ch :game/join ch)
      (start-read-loop this ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/unsub route-pub-ch :game/join ch)
      (assoc this :ch nil))))

(defn new-join-game []
  (component/using (map->JoinGame {}) [:server :clients]))
