(ns me.moocar.ftb500.server.routes.add-game
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defn- new-seat-tx [game-db-id position]
  (let [seat-db-id (d/tempid :db.part/ftb500)
        seat-id (d/squuid)]
    [[:db/add seat-db-id :seat/id seat-id]
     [:db/add seat-db-id :seat/position position]
     [:db/add game-db-id :game/seats seat-db-id]]))

(defn process
  [this]
  (let [{:keys [tx-ch]} (:datomic-conn this)]
    (fn [context result-ch]
      (let [{:keys [msg send-ch session-id]} context
            {game-id :game/id} (:keys msg)
            game-db-id (d/tempid :db.part/ftb500)
            tx (concat
                [[:db/add game-db-id :game/id game-id]]
                (mapcat #(new-seat-tx game-db-id %) (range 4)))
            response-ch (async/chan)]
        (go
          (>! tx-ch [tx response-ch])
          (<! response-ch)
          (>! send-ch msg)
          (async/close! result-ch))))))

(defrecord AddGame [;; Configuration
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

(defn new-add-game []
  (component/using (map->AddGame {:route :game/add})
    {:server :me.moocar.ftb500/server
     :datomic-conn :datomic-conn
     :log-ch :log-ch}))
