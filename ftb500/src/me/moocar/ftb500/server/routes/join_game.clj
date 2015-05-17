(ns me.moocar.ftb500.server.routes.join-game
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.server.clients :as clients]))

(defn process
  [this]
  (fn [context result-ch]
    (let [{:keys [datomic-conn clients]} this
          {:keys [tx-ch conn]} datomic-conn
          db (d/db conn)
          {:keys [msg send-ch session-id]} context
          {game-id :game/id seat-id :seat/id} (:keys msg)
          game (d/entity db [:game/id game-id])
          session (d/entity db [:session/id session-id])
          user (d/entity db [:user/id (:session.user/id session)])
          tx (if seat-id
               (let [seat (d/entity db [:seat/id seat-id])]
                 [[:db/add (:db/id seat) :seat/user (:db/id user)]])
               [:game/join (:db/id user) (:db/id game)])
          response-ch (async/chan)]
      (go
          (>! tx-ch [tx response-ch])
          (<! response-ch)
          (clients/add-client clients game-id send-ch)
          (async/close! result-ch)))))

(defrecord JoinGame [;; Configuration
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
  (component/using (map->JoinGame {:route :game/join})
    {:server :me.moocar.ftb500/server
     :datomic-conn :datomic-conn
     :clients :clients}))
