/*
Programmer: Aniket Panda
Course: CSC 583
Description: Builds an index and allows queries on it using
Lucene. Performs the serious computation for the Watson
project. Data for building the index comes from the resources
folder.
*/

package edu.arizona.cs;

// import Lucene libraries
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;

// import CoreNLP libraries
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import java.util.*;

// import other libraries
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;


// The class used to build and search the index
public class QueryEngine {

    // array of suffix identifiers for wiki files
    String[] wikiFileNums = {"0005","0006","0007","0009","0013","0014","0016","0017","0018",
    "0020","0021","0022","0025","0026","0032","0033","0034","0035","0039","0040","0042",
    "0043","0048","0049","0051","0052","0053","0054","0057","0058","0062","0067","0087",
    "0088","0093","0095","0098","0099","0100","0103","0115","0123","0124","0125","0128",
    "0129","0130","0131","0132","0136","0146","0151","0153","0174","0185","0203","0215",
    "0216","0226","0268","0348","0363","0540","0555","0567","0593","0597","0689","0714",
    "0766","0806","0813","0833","0841","0856","0964","0985","1069","1129","1259"};

    // Lucene analyzer objects
    StandardAnalyzer analyzer;
    EnglishAnalyzer stemAnalyzer;

    // specifies 1, 2, or 3 for stemming, lemmatization, or biword mode
    int stemOrLem;
    Directory index;

    // sim may change change based on which scoring method we use
    Similarity sim = new BM25Similarity();
    IndexWriterConfig config;

    /*
    Constructor sets stemOrLem to stemLem, changes the similarity to
    cosine similarity if classicScoring is true, and calls buildIndex() 
    to build the index.
    */
    public QueryEngine(boolean classicScoring, int stemLem){
        if (classicScoring) {
            sim = new ClassicSimilarity();
        }
        stemOrLem = stemLem;
        buildIndex();
    }

    // helper function that lemmatizes text
    private String lemmatize(String text) {
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create a document object
        CoreDocument document = pipeline.processToCoreDocument(text);
        // buffer to build lemmatized string in
        StringBuffer sb = new StringBuffer();
        // add tokens to buffer
        for (CoreLabel tok : document.tokens()) {
            sb.append(tok.lemma());
            sb.append(' ');
        }
        // return buffer
        return sb.toString();
    }

    // buildIndex() helper method that adds docs to index
    private void addDoc(IndexWriter w, String content, String docid) throws IOException {
        // use text field for content because we want to tokenize it
        Document doc = new Document();
        // if lemmatization
        if (stemOrLem == 2) {
            doc.add(new TextField("content", lemmatize(content), Field.Store.YES));
        }
        // otherwise
        else {
            doc.add(new TextField("content", content, Field.Store.YES));
        }

        // use a string field for docid because we don't want it tokenized
        doc.add(new StringField("docid", docid, Field.Store.YES));
        w.addDocument(doc);
    }

    // helper function to convert string arg biword proximity search query
    private String biword(String str) {
        // string buffer to build string
        StringBuffer sb = new StringBuffer();
        // convert string to array of tokens
        String[] arr = str.trim().toLowerCase().replaceAll("[^a-zA-Z0-9 ]", " ").split("\\s++");
        // fill buffer with biword proximity phrases
        for (int i = 0; i < arr.length-1; i++) {
            sb.append("\"" + arr[i] + " AND " + arr[i+1] + "\"~2 ");
        }
        // return query
        return sb.toString();
    }

    // Builds the index using the instance variables
    private void buildIndex() {
        // instantiate Lucene objects
        index = new ByteBuffersDirectory();
        IndexWriterConfig config;

        // use EnglishAnalyzer for stemming
        if (stemOrLem == 1) {
            stemAnalyzer = new EnglishAnalyzer();
            config = new IndexWriterConfig(stemAnalyzer);
        }
        // StandardAnalyzer otherwise
        else {
            analyzer = new StandardAnalyzer();
            config = new IndexWriterConfig(analyzer);
        }

        // set scoring strategy
        config.setSimilarity(sim);

        try {
            // index writer to write docs in
            IndexWriter w = new IndexWriter(index, config);

            // loop over all wiki article files
            for (int i = 0; i < wikiFileNums.length; i++) {
                // build file name
                String fileName = "enwiki-20140602-pages-articles.xml-" + wikiFileNums[i] + ".txt";
                System.out.println(fileName);

                // get file from resources folder
                ClassLoader classLoader = getClass().getClassLoader();
                File file = new File(classLoader.getResource(fileName).getFile());

                try (Scanner inputScanner = new Scanner(file)) {
                    // variables of each document
                    int counter = 0;
                    String docID = "";
                    StringBuffer sb = new StringBuffer();

                    // scan each line and add to doc
                    while (inputScanner.hasNextLine()) {
                        String line = inputScanner.nextLine();
                        if (line.length() < 4) { continue; }

                        // if double brackets, new doc started
                        if (line.substring(0,2).equals("[[") &&
                            line.substring(line.length()-2,line.length()).equals("]]")) {

                            // if not first doc start, add old doc to index
                            if (counter > 0) {
                                addDoc(w, sb.toString(), docID);
                                sb.delete(0, sb.length());
                            }

                            // save docID and increment counter
                            docID = line;
                            counter++;
                        }
                        // otherwise, append line to current doc
                        else {
                            sb.append(' ');
                            sb.append(line);
                        }
                    }

                    // add last doc because there is no new tag coming
                    addDoc(w, sb.toString(), docID);
                    sb.delete(0, sb.length());              

                    System.out.println("Docs in file: " + counter);
                    inputScanner.close();
                } 
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            w.close();
        }
        catch ( Exception ex)
        {
            System.out.println(ex.getMessage()); 
        }
    }

    /*
    Takes a string and queries the index based on it. Relies on instance variables to 
    specify scoring strategy and whether to stem, lemmatize, or use biword search.
    Returns a single result because we are using precision@1.
    */
    public ResultClass runQuery(String queryStr) throws java.io.FileNotFoundException,java.io.IOException {
        try {
            // query to input into searcher
            Query q;

            // if stemming
            if (stemOrLem == 1) {
                QueryParser parser = new QueryParser("content", stemAnalyzer);
                String escaped = parser.escape(queryStr);
                q = parser.parse(escaped);
            }
            // if lemmatization
            else if (stemOrLem == 2) {
                QueryParser parser = new QueryParser("content", analyzer);
                String escaped = parser.escape(lemmatize(queryStr));
                q = parser.parse(escaped);
            }
            // if biword search
            else if (stemOrLem == 3) {
                QueryParser parser = new QueryParser("content", analyzer);
                q = parser.parse(biword(queryStr));
            }
            // if none of the above
            else {
                QueryParser parser = new QueryParser("content", analyzer);
                String escaped = parser.escape(queryStr);
                q = parser.parse(escaped);
            }

            // instantiate the reader/searcher and set the similarity
            int hitsPerPage = 1;
            IndexReader reader = DirectoryReader.open(index);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(sim);

            // get the results
            TopDocs docs;
            try {
                docs = searcher.search(q, hitsPerPage);
            }
            catch (Exception ex) {
                ResultClass result = null;
                System.out.println("CATCH!");
                return result;
            }
            ScoreDoc[] hits = docs.scoreDocs;

            // return docId and score as result
            Document d = searcher.doc(hits[0].doc);
            ResultClass result = new ResultClass();
            result.DocName = d;
            result.docScore = hits[0].score;
            return result;
        }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        // return null if search failed
        return null;
    }
}
