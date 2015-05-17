(ns me.moocar.ftb500.server.routes.get-game
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defn process
  [this]
  (let [{:keys [conn]} (:datomic-conn this)]
    (fn [context result-ch]
      (let [{:keys [msg send-ch]} context
            {game-id :game/id} (:keys msg)
            db (d/db conn)]
        (go
          (try
            (when-let [game (d/pull db [{:game/seats [:seat/id :seat/position]}] [:game/id game-id])]
              (>! send-ch (assoc msg :body game)))
            (finally
              (async/close! result-ch))))))))

(defrecord GetGame [;; Configuration
                    route
                    ;; Dependencies
                    server log-ch datomic-conn
                    ;; After started
                    ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan)]
      (async/sub route-pub-ch route ch)
      (async/pipeline-async 5 (async/chan (async/dropping-buffer 1))
                            (process this)
                            ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/close! ch)
      (async/unsub route-pub-ch route ch)
      (assoc this :ch nil))))

(defn construct []
  (component/using (map->GetGame {:route :game/get})
    {:server :me.moocar.ftb500/server
     :datomic-conn :datomic-conn}))
