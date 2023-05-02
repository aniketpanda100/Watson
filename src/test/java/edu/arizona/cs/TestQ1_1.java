/*
Programmer: Aniket Panda
Course: CSC 583
Description: Serves as the main() function for the Watson
project. Contains a single function that declares an instance
of QueryEngine, then loops through questions.txt, using each
question to query the QueryEngine index and print out the 
results to the command line.
*/

package edu.arizona.cs;

// import misc. libraries
import java.util.List;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

// acts as main() function for project
public class TestQ1_1 {

    // true for cosine similarity, false for BM25 probablistic
    boolean scoringStrategy = false;
    // 0, 1, 2, and 3 correspond to none of following, stemming
    // lemmatization, and biword search respectively
    int tokenNorm = 0;

    @Test
    /*
    Loops through the questions in the questions.txt file, inputs each
    one as a query to the QueryEngine instance, then prints out the 
    correct answer, the returned answer, and the score to the command
    line.
    */
    public void testDocsAndScores() {
        // contains index
        QueryEngine objQueryEngine = new QueryEngine(scoringStrategy, tokenNorm);

        //Get questions file from resources folder
        ClassLoader classLoader = getClass().getClassLoader();

        // store Jeopardy questions and answers in separate, parallel lists
        ArrayList<String> questions = new ArrayList<String>();
        ArrayList<String> answers = new ArrayList<String>();

        try {
            // parse questions and answers from questions.txt
            File qFile = new File(classLoader.getResource("questions.txt").getFile());
            try (Scanner qScanner = new Scanner(qFile)) {
                while (qScanner.hasNextLine()) {
                    // concatentate category and clue into one question
                    questions.add((qScanner.nextLine() + ' ' + qScanner.nextLine()));
                    // add answer to its own list
                    answers.add(qScanner.nextLine().trim());
                    // skip blank line
                    qScanner.nextLine();
                }
                qScanner.close();
            } catch (IOException e) {
                    e.printStackTrace();
            }
        }
        catch ( Exception ex)
        {
            System.out.println(ex.getMessage()); 
        }

        try {
            // submit each question to the index and print it and the results out
            for (int i = 0; i < questions.size(); i++) {
                ResultClass result = objQueryEngine.runQuery(questions.get(i));
                // if null result, print null
                if (result == null) {
                    System.out.println(answers.get(i) + '\t' + "NULL" 
                        + ',' + "NULL" + ',');
                    continue;
                }
                // otherwise, print normal results
                System.out.println(answers.get(i) + ',' 
                    + ((("" + result.DocName.get("docid")).replace("[", "")).replace("]", ""))
                    + ',' + result.docScore + ',');
            }

            assertEquals(0,0); 
        }
        catch ( java.io.FileNotFoundException ex)
        {
            System.out.println(ex.getMessage()); 
        }
        catch ( java.io.IOException ex)
        {
            System.out.println(ex.getMessage()); 
        }
    }
}
