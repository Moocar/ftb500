(ns me.moocar.ftb500.server.routes.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.server.routes.add-game :as add-game]
            [me.moocar.ftb500.server.routes.connect :as connect]
            [me.moocar.ftb500.server.routes.add-user :as add-user]
            [me.moocar.ftb500.server.routes.get-game :as get-game]
            [me.moocar.ftb500.server.routes.join-game :as join-game]))

(defn new-system
  []
  (component/system-map
   :game/add (add-game/new-add-game)
   :user/connect (connect/construct)
   :game/get (get-game/new-get-game)
   :game/join (join-game/new-join-game)
   :user/add (add-user/construct)))
