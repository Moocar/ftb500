(ns me.moocar.ftb500.client.app
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [<!]]
            [cljs-uuid-utils.core :refer [make-random-squuid]]
            [cognitect.transit :as transit]
            [me.moocar.remote :as remote])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(defn atom-input [value]
  [:input {:type "text"
           :value @value
           :on-change (fn [e]
                        (reset! value (-> e .-target .-value)))}])

(defn current-page [client]
  (let [val (atom "enter here")]
    [:div
     [:h2 "What is your name?"]
     [atom-input val]
     [:input {:type "button"
              :value "Set name"
              :on-click (fn [click]
                          (let [response-ch (async/chan)]
                            (remote/post client
                                         {:route :user/add
                                          :keys {:user/id (make-random-squuid)
                                                 :user/name @val
                                                 }}
                                         response-ch)
                            (.log js/console "sent to server")
                            (go
                              (.log js/console (str (<! response-ch))))))}]]))

(defn mount-root [client]
  (reagent/render [current-page client] (.getElementById js/document "app")))

(defn listener
  "Returns a websocket listener that does nothing but put connections,
  reads or errors into the respective channels"
  [ws conn]
  (let [{:keys [connect-ch read-ch error-ch]} conn]
    (.log js/console "starting listener")
    (doto ws
      (aset "onopen"
            (fn [event]
              (.log js/console "opened")
              (async/put! connect-ch event)))
      (aset "onerror"
            #(async/put! error-ch %))
      (aset "onmessage"
            #(async/put! read-ch (aget % "data")))
      (aset "onclose"
            (fn [event]
              (async/put! connect-ch
                          [(aget event "code") (aget event "reason")]))))))

(defn conn-loop
  "Waits for a connection to occur, and then loops waiting for IO.
  Incoming messages to the server are consumed from the connection's
  read-ch. They are converted into a clojure data structure and put
  onto recv-ch. Outgoing messages are consumed from the connection's
  send-ch, converted to a byte buffer, and sent using the session's
  remote. The loop finishes once a disconnect occurs"
  [ws transport-chans conn]
  (let [{:keys [error-ch connect-ch read-ch log-ch]} conn
        {:keys [send-ch recv-ch]} transport-chans]
    (go
      (when-let [session (<! connect-ch)]
        (loop []
          (alt!

            read-ch
            ([data]
             (when data
               (try
                 (let [reader (transit/reader :json)
                       request (transit/read reader data)
                       msg-wrapper {:request request}]
                   (>! log-ch (str msg-wrapper))
                   (>! recv-ch msg-wrapper))
                 (catch js/Error t
                   (>! log-ch {:error t})
                   (async/put! error-ch t)))
               (recur)))

            send-ch
            ([request]
             (when request
               (let [writer (transit/writer :json)
                     msg (transit/write writer request)]
                 (.log js/console "sending")
                 (.send ws msg))
               (recur)))

            connect-ch
            ([[status-code reason]]
             (>! log-ch {:connect! [status-code reason]})
             (async/close! connect-ch))))))))

(defn start-ws
  [this]
  (let [{:keys [uri transport-chans log-ch]} this
        WebSocket (or (aget js/window "WebSocket")
                      (aget js/window "MozWebSocket"))
        ws (WebSocket. uri)
        conn {:log-ch log-ch
              :read-ch (async/chan)
              :connect-ch (async/chan)
              :error-ch (async/chan)}]
    (listener ws conn)
    (.log js/console ws)
    (conn-loop ws transport-chans conn)
    (assoc this :conn conn)))

(defn start-client
  [this]
  (let [{:keys [server-chans]} this
        mult (async/mult (:recv-ch server-chans))]
    (assoc this :mult mult)))

(defn start []
  (.log js/console "mounted")
  (let [transport-chans {:send-ch (async/chan)
                         :recv-ch (async/chan)}
        log-ch (async/chan)
        client {:server-chans transport-chans
                :log-ch log-ch}
        client (start-client client)
        ws {:uri "ws://localhost:8080/ws"
            :transport-chans transport-chans
            :log-ch log-ch}
        ws (start-ws ws)]
    (go
      (loop []
        (when-let [msg (<! log-ch)]
          (.log js/console msg)
          (recur))))
    (mount-root client)
    (.log js/console "mounted")))
