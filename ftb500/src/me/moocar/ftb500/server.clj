(ns me.moocar.ftb500.server)

(defrecord PubServer [recv-ch ; dependency
                      route-pub-ch ; after started
                      ]
  component/Lifecycle
  (start [this]
    (if route-pub-ch
      this
      (let [route-pub-ch (async/pub recv-ch (comp :route :msg))]
        (assoc this :route-pub-ch route-pub-ch))))
  (stop [this]
    (if route-pub-ch
      (do (async/unsub-all route-pub-ch)
          (assoc this :route-pub-ch nil))
      this)))

(defn new-server
  [config recv-ch]
  (map->PubServer {:recv-ch recv-ch}))
