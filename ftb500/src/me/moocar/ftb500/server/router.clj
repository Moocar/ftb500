(ns me.moocar.ftb500.server.router
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
            [io.pedestal.interceptor :as i]
            [io.pedestal.impl.interceptor :as ii]
            [me.moocar.ftb500.server.routes.user :as user]
            [me.moocar.ftb500.server.routes.game :as game]
            [datomic.api :as d]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptors

(defn db []
  {:components [:datomic-conn]
   :interceptor
   {:enter (fn [{:keys [components] :as context}]
             (let [{:keys [datomic-conn]} components]
               (assoc context :db (d/db (:conn datomic-conn)))))}})

(defn load-session []
  {:dependencies [:db]
   :interceptor
   {:enter (fn [{:keys [db session-id] :as context}]
             (let [session (d/entity db [:session/id session-id])]
               (assoc context :session session)))}})

(defn load-user []
  {:dependencies [:db :load-session]
   :interceptor {:enter (fn [{:keys [db session] :as context}]
                          (let [user-id (:session.user/id session)
                                user (d/entity db [:user/id user-id])]
                            (assoc context :user user)))}})

(defn handle-error []
  {:error (fn [{:keys [send-ch request] :as context} error]
            (let [log-ch (get-in context [:components :log-ch])]
              (go (>! log-ch {:context context :error error})
                  (>! send-ch (assoc request :error {:type :server-error}))
                  nil)))})

(defn invalid-data []
  {:error (fn [{:keys [send-ch request] :as context} error]
            (if (= :invalid-data (:reason (ex-data error)))
              (let [{:keys [required]} (ex-data error)
                    log-ch (get-in context [:components :log-ch])]
                (go (>! log-ch {:request request
                                :error (ex-data error)})
                    (>! send-ch (assoc request :error {:required required})) nil))
              (throw error)))})

(def routes-spec
  {:db (db)
   :load-session (load-session)
   :load-user (load-user)
   :me.moocar.websocket/connect (user/connect)
   :user/add (user/add)
   :game/get (game/get)
   :game/add (game/add)
   :game/join (game/join)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Plumbing

(defn add-system [system]
  {:enter (fn [context]
            (assoc context :components system))})

(defn dependency-graph [spec deps]
  (reduce-kv (fn [graph key interceptor]
               (reduce (fn [graph dependency-k]
                         (dep/depend graph key dependency-k))
                       graph
                       (:dependencies interceptor)))
             (dep/graph)
             (select-keys spec deps)))

(defn calc-interceptors
  [route routes-spec]
  (let [interceptor (get routes-spec route)
        dependencies (:dependencies interceptor)
        graph (dependency-graph routes-spec (keys routes-spec))
        route-deps (dep/transitive-dependencies graph route)
        ordered-deps (sort (dep/topo-comparator graph) (conj route-deps route))]
    (map (fn [interceptor-k]
           (:interceptor (get routes-spec interceptor-k)))
         ordered-deps)))

(defn add-route [system routes-spec]
  {:enter (fn [context]
            (ii/enqueue* context (calc-interceptors (get-in context [:request :route]) routes-spec)))})

(defn handle
  [system context]
  (async/put! (:log-ch system)
              (select-keys context [:request :session-id]))
  (-> context
      (ii/enqueue (handle-error)
                  (add-system system)
                  (invalid-data)
                  (add-route system routes-spec))
      ii/execute))

(defn parallel [n f ch]
  (async/pipeline n
                  (async/chan (async/dropping-buffer 1))
                  (keep f)
                  ch))

(defrecord Router [server-recv-ch]
  component/Lifecycle
  (start [this]
    (parallel 20 #(handle this %) server-recv-ch)
    this)
  (stop [this]
    this))
