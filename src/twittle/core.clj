(ns twittle.core
  (:import (twitter4j TwitterFactory)
           [javax.swing JList])
  (:use [overtone.at-at]
        [seesaw.core]))

(def tweet-pool (mk-pool)) ;pool of threads for pulling tweets down
(def processor-pool (mk-pool)) ;pool of threads for processing tweets
(def gui-pool (mk-pool)) ;pool of threads for processing tweets
(def retweet-factor 5) ;anything with more than 5 retweets is considered important

(def f (frame
         :title "Twittle",
         :content (JList.),
         :width 600,
         :height 400,
         :on-close :exit ))

(def tweets (ref []))
(def important-tweets (ref []))

(defn get-twitter
  "Get the twitter instance"
  []
  (twitter4j.TwitterFactory/getSingleton))

(defn get-timeline
  "Get the users timeline"
  []
  (into [] (.getHomeTimeline (get-twitter) (twitter4j.Paging. (int 1) (int 200))))) ;into will convert the java collection into a vector

(defn load-tweets-async
  "Load the users tweets in a background thread"
  [period]
  (every period #(
                   dosync
                   (ref-set tweets
                     (map (fn [x] {:content (.getText x) :retweets (.getRetweetCount x)}) (get-timeline)))) tweet-pool))

(defn process-tweets
  "Filter out any tweets that arent important"
  [cutoff period]
  (every period #(
                   dosync
                   (ref-set important-tweets (reverse (sort-by :retweets (filter (fn [x] (< 5 (x :retweets ))) @tweets))))) processor-pool))

(defn update-gui
  "Update the GUI with the latest important tweets"
  [period]
  (every period #(config! f :content (JList. (to-array @important-tweets))) gui-pool))

(defn -main [& args]
  ;display as natively as we can
  (native!)
  ;show the GUI
  (-> f show!)

  ;kick off the loading of the tweets
  (load-tweets-async 10000)

  ;kick off the thread that processes the tweets
  (process-tweets retweet-factor 1000)

  ;kick off the thread that updates the UI
  (update-gui 1000))