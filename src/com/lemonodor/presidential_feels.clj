(ns com.lemonodor.presidential-feels
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [com.climate.claypoole :as cp])
  (:import
   (java.io File)
   (java.util Properties)
   (edu.stanford.nlp.ling
    CoreAnnotations$SentencesAnnotation
    CoreAnnotations$TextAnnotation
    CoreLabel)
   (edu.stanford.nlp.neural.rnn
    RNNCoreAnnotations)
   (edu.stanford.nlp.pipeline
    Annotation
    StanfordCoreNLP)
   (edu.stanford.nlp.sentiment
    SentimentCoreAnnotations$SentimentClass)
   (edu.stanford.nlp.trees
    LabeledScoredTreeNode
    Tree))
  (:gen-class))


(defn pipeline-props
  "Returns Properties for a sentiment analysis pipeline."
  ^java.util.Properties []
  (doto (Properties.)
    (.setProperty
     "annotators"
     "tokenize, ssplit, parse, sentiment")))


(defn make-pipeline
  "Returns a sentiment analysis pipeline."
  ^edu.stanford.nlp.pipeline.StanfordCoreNLP []
  (StanfordCoreNLP. (pipeline-props)))


(defn set-sentiment-labels! [^Tree tree]
  (when-not (.isLeaf tree)
    (doseq [child (.children tree)]
      (set-sentiment-labels! child))
    (let [label (.label tree)]
      (when-not (instance? CoreLabel label)
        (throw (Exception. (str "label " label " of " tree
                                " is not a CoreLabel."))))
      (let [^CoreLabel l label]
        (.setValue l (str (RNNCoreAnnotations/getPredictedClass tree)))))))


(defn conv-tree [^LabeledScoredTreeNode tree]
  (let [^Tree t (.deepCopy tree)]
    (set-sentiment-labels! t)
    (.toString t)))


(defn conv-ann [^Annotation ann]
  (for [key (.keySet ann)]
    [key
     (let [v (.get ann key)]
       [(type v)
        (if (instance? LabeledScoredTreeNode v)
          (conv-tree v)
          v)])]))


(defn text-sentiment
  "Performs sentiment analysis on text.

  Takes a string that contains one or more sentences and returns a
  sequence of [<sentence text> <sentiment>] pairs."
  [^String text ^StanfordCoreNLP pipeline pool]
  (let [^Annotation doc (Annotation. text)]
    (.annotate pipeline doc)
    (let [sentences (.get doc CoreAnnotations$SentencesAnnotation)]
      (log/info "    Found" (count sentences) "sentences")
      (map
       (fn [^Annotation sentence]
              [(.get sentence CoreAnnotations$TextAnnotation)
               (.get sentence SentimentCoreAnnotations$SentimentClass)])
       sentences))))


(defn sentiment-class-to-css-class [class]
  (string/replace (string/lower-case class)
                  " "
                  "-"))


(defn para-sentiment-to-html [para-sent]
  (str "<p>\n"
       (string/join
        (map (fn [[sentence sentiment]]
               (str "  <span class=\""
                    (sentiment-class-to-css-class sentiment)
                    "\">"
                    (string/escape sentence {\< "&lt;", \> "&gt;"})
                    "  </span>\n"))
             para-sent))
       "</p>\n")
  )


(defn para-sentiments-to-html [para-sentiments]
  (string/join
   (map para-sentiment-to-html para-sentiments)))


(defn split-paragraphs
  "Splits a string containing an NLTK inaugural address into
  paragraphs."
  [s]
  (map string/trim
       (string/split s #"(?m)^\b*$")))


(defn file-sentiments
  "Performs sentiment analysis on an NLTK inaugural address file.

  Takes a path, returns a sequence with an element for each paragraph,
  where each element is a sequence of [<sentence> <sentiment>] pairs."
  [path pipeline pool]
  (log/info "Processing" path)
  (let [paras (-> path
                  slurp
                  split-paragraphs)
        _ (log/info "  Found" (count paras) "paragraphs")
        para-sentiments (->> paras
                             (cp/pmap
                              pool
                              #(text-sentiment % pipeline pool)))]
    para-sentiments))


(defn basename [path]
  (.getName (io/file path)))


(defn parse-filename
  "Parses a path like 'foo/2009-Obama.txt' into [\"Obama\" \"2009\"]."
  [path]
  (let [m (re-matches #"([0-9]+)-([^\.]+)\..+" (basename path))]
    (reverse (drop 1 m))))


(defn analyze-file [path pipeline pool]
  (let [[president year] (parse-filename path)]
    {:president president
     :year year
     :filename (basename path)
     :paras (file-sentiments path pipeline pool)}))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [pipeline (make-pipeline)]
    (cp/with-shutdown! [pool (cp/threadpool (+ 2 (cp/ncpus)))]
      (json/pprint (map #(analyze-file % pipeline pool) args))))
  (shutdown-agents))
