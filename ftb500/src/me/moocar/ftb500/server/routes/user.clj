(ns me.moocar.ftb500.server.routes.user
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [datomic.api :as d]))

(defn connect []
  {:dependencies [:db :load-user]
   :interceptor
   {:enter
    (fn [{:keys [db user send-ch] :as context}]
      (go
        (let [user-details (d/pull db '[:user/id :user/name] (:db/id user))]
          (>! send-ch {:route :user/add
                       :keys user-details})
          nil)))}})

(defn add []
  {:dependencies [:db :load-session]
   :interceptor
   {:enter
    (fn [{:keys [db user send-ch session-id request] :as context}]
      (let [tx-ch (get-in context [:components :datomic-conn :tx-ch])
            {user-id :user/id user-name :user/name} (:keys request)]
        (when-not (and user-id user-name)
          (throw (ex-info "Invalid data" {:reason :invalid-data
                                          :required [:user/id :user/name]})))
        (let [tx [{:db/id #db/id[:db.part/session]
                   :session/id session-id
                   :session.user/id user-id}
                  {:db/id #db/id[:db.part/ftb500]
                   :user/id user-id
                   :user/name user-name}]
              response-ch (async/chan)]
          (go
            (>! tx-ch [tx response-ch])
            (<! response-ch)
            (>! send-ch request)
            nil))))}})
