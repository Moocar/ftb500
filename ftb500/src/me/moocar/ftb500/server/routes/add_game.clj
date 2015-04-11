(ns me.moocar.ftb500.server.routes.add-game
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [com.stuartsierra.component :as component]))

(defn start-read-loop [this ch]
  (println "starting read loop" this)
  (go-loop []
    (println "go looping")
    (when-let [request (<! ch)]
      (println "adding game!!!" request)
      ;; Success
      (>! (:send-ch request) (:msg request))
      (recur))))

(defrecord AddGame [route-pub-ch ch]
  component/Lifecycle
  (start [this]
    (let [ch (async/chan 1)]
      (async/sub route-pub-ch :game/add ch)
      (start-read-loop this ch)
      (assoc this :ch ch)))
  (stop [this]
    (println "topping add game")
    (async/unsub route-pub-ch :game/add ch)
    (assoc this :ch nil)))

(defn new-add-game []
  (component/using (map->AddGame {}) [:route-pub-ch]))
