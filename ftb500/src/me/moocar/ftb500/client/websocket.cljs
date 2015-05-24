(ns me.moocar.ftb500.client.websocket
  (:require [cljs.core.async :as async :refer [<!]]
            [cognitect.transit :as transit])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(defn listener
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

(defn start
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
