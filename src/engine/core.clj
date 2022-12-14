(ns engine.core
  (:require [engine.utils :refer (set-var-root recursively-search-files)]
            [engine.graphics :as g]
            [engine.input :refer (update-mousebutton-state)])
  (:import [com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration]
           [com.badlogic.gdx Gdx Screen Game]
           [com.badlogic.gdx.assets AssetManager]
           [com.badlogic.gdx.audio Sound]))

(defn ^:private create-lwjgl3-application
  [& {:keys [title game width height full-screen]}]
  (let [config (Lwjgl3ApplicationConfiguration.)]
    (.setTitle config title)
    (.useVsync config true)
    (if full-screen
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    (println "Starting Lwjgl3Application")
    (Lwjgl3Application. game config)))

(defn exit-app []
  (.exit (Gdx/app)))

(defmacro with-context [& body]
   `(.postRunnable (Gdx/app)
                   (fn [] ~@body)))

(defn get-fps []
  (str (.getFramesPerSecond (Gdx/graphics))))

(declare ^:private ^AssetManager asset-manager)

(defn- load-sounds [assets-folder]
  (let [sounds (recursively-search-files assets-folder #{"wav"})]
    (println "Found" (count sounds) "sounds.")
    (dorun (map #(.load asset-manager % Sound) sounds)))
  (println "Loading all sounds...")
  (time (.finishLoading asset-manager)))

(def ^:private get-sound
  (memoize
   (fn [spath]
     (.get asset-manager (str "sounds/" spath) Sound))))

(defn play-sound [filestring]
  (.play (get-sound filestring)))

(defprotocol GameScreen
  "A game can have different screens like main-menu, player-selection, actual ingame-state, and so on.
  Graphics context is available in all functions for loading/disposing of resources."
  (show [this]
        "Called each time this screen is shown.")
  (destroy [this]
           "Called once on closing the game app")
  (render [this]
          "Called on every tick before update-screen")
  (update-screen [this delta]
                 "Called on every tick with elapsed delta time in ms"))

(defn- game-screen->libgdx-screen [game-screen]
  (reify Screen
    (show [_]
      (show game-screen))
    (render [_ delta]
      (g/clear-screen)
      ; TODO check if resize happened and resize was not called (maximize, or move window)
      ; compare screen width to viewport set screenwidth size maybe
      (render game-screen)
      (update-mousebutton-state)
      (update-screen game-screen (* delta 1000)))
    (resize [_ w h])
    (pause [_])
    (resume [_])
    (hide [_])
    (dispose [_]
      (destroy game-screen))))

; hash of keyword -> screen
(declare ^:private screens)

(defn set-screen [k]
  (.setScreen (.getApplicationListener (Gdx/app))
              (k screens)))

(defn start-app
  [& {:keys [game-screens
             full-screen
             title
             window-width
             window-height
             viewport-width
             viewport-height
             assets-folder
             on-create]
      :or {title "Test"
           window-width 800
           window-height 600
           viewport-width 800
           viewport-height 600
           on-create (fn [])}}]
  (let [screens (zipmap
                 (keys game-screens)
                 (map game-screen->libgdx-screen (vals game-screens)))
        game (proxy [Game] []
               (create []
                 (set-var-root #'asset-manager (AssetManager.))
                 (load-sounds assets-folder)
                 (g/initialize viewport-width viewport-height asset-manager assets-folder)
                 (on-create)
                 (.setScreen this (first (vals screens))))
               (dispose []
                 (.dispose asset-manager)
                 (g/on-dispose)
                 (dorun (map (memfn dispose) (vals screens))))
               (pause [])
               (resize [w h]
                 (g/on-resize w h))
               (resume []))]
    (set-var-root #'screens screens)
    (create-lwjgl3-application :title title
                               :game game
                               :width window-width
                               :height window-height
                               :full-screen full-screen)))
