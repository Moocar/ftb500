(ns me.moocar.ftb500.server.routes.game
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [datomic.api :as d]
            [me.moocar.ftb500.server.clients :as clients])
  (:refer-clojure :exclude [get]))

(defn- new-seat-tx [game-db-id position]
  (let [seat-db-id (d/tempid :db.part/ftb500)
        seat-id (d/squuid)]
    [[:db/add seat-db-id :seat/id seat-id]
     [:db/add seat-db-id :seat/position position]
     [:db/add game-db-id :game/seats seat-db-id]]))

(defn get []
  {:dependencies [:db]
   :interceptor
   {:enter (fn [{:keys [db request send-ch] :as context}]
             (let [{game-id :game/id} (:keys request)]
               (when-not game-id
                 (throw (ex-info "Invalid data" {:reason :invalid-data
                                                 :required [:game/id]})))
               (go
                 (when-let [game (d/pull db
                                         [{:game/seats [:seat/id :seat/position]}]
                                         [:game/id game-id])]
                   (>! send-ch (assoc request :body game))))))}})



(defn add []
  {:dependencies [:db :load-user]
   :interceptor
   {:enter
    (fn [{:keys [db send-ch request] :as context}]
      (let [{game-id :game/id} (:keys request)]
        (when-not game-id
          (throw (ex-info "Invalid data" {:reason :invalid-data
                                          :required [:game/id]})))
        (let [tx-ch (get-in context [:components :datomic-conn :tx-ch])
              game-db-id (d/tempid :db.part/ftb500)
              tx (concat
                  [[:db/add game-db-id :game/id game-id]]
                  (mapcat #(new-seat-tx game-db-id %) (range 4)))
              response-ch (async/chan)]
          (go
            (>! tx-ch [tx response-ch])
            (<! response-ch)
            (>! send-ch request)))))}})

(defn join []
  {:dependencies [:db :load-user]
   :interceptor
   {:enter
    (fn [{:keys [db request send-ch user] :as context}]
      (let [tx-ch (get-in context [:components :datomic-conn :tx-ch])
            clients (get-in context [:components :clients])
            {game-id :game/id seat-id :seat/id} (:keys request)
            game (d/entity db [:game/id game-id])
            tx (if seat-id
                 (let [seat (d/entity db [:seat/id seat-id])]
                   [[:db/add (:db/id seat) :seat/user (:db/id user)]])
                 [:game/join (:db/id user) (:db/id game)])
            response-ch (async/chan)]
        (go
          (>! tx-ch [tx response-ch])
          (<! response-ch)
          (clients/add-client clients game-id send-ch))))}})
