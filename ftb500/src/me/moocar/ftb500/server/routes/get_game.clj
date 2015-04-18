(ns me.moocar.ftb500.server.routes.get-game
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [com.stuartsierra.component :as component]))

(defn start-read-loop [this ch]
  (go-loop []
    (when-let [request (<! ch)]
      ;; Success
      (>! (:send-ch request) (assoc (:msg request) :game/seats [{:id "seat1"}
                                                                {:id "seat2"}]))
      (recur))))

(defrecord GetGame [server ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan 1)]
      (async/sub route-pub-ch :game/get ch)
      (start-read-loop this ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/unsub route-pub-ch :game/get ch)
      (assoc this :ch nil))))

(defn new-get-game []
  (component/using (map->GetGame {}) [:server]))
