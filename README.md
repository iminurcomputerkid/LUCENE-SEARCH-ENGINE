# Names: Riley Pierson, Julien Niles, Zi'Aire Tirado

*cranfield* directory contains the Cranfield data set<p>
*data* directory contains the Project Gutenberg files.  They are numbered pgxxx.txt and pgxxxx.txt.<p>
*lib* directory contains the Lucene 8.8.2 jar files
*/src/edu/siena/csis225/projects25*  contains program source code

**Abstract**<p>
Our project goals involve implementing a custom document retrieval system that efficiently indexes and searches extensive text-based data using Apache Lucene’s capabilities. Our system utilizes one of Lucene’s  ranking algorithms, TF-IDF, to index several critical fields of interest, including content, author, and title. After indexing, we expect our system to return relevant search results based on user-inputted queries. 

The star next to the feature means bonus implementations.

**Features**<p>
Support for Extensive Searching Options: Fielded Queries, Term Queries, Boolean Queries and Phrase Queries<p>
TF-IDF Scoring using ClassicSimilarity<p>
*Parallel Indexing<p>
*CranfieldQAEvaluator for calculating Macro-F1 and Micro-F1 scores (measures precision and recall)<p>
Flexible Indexing Modes: "new", "updated", "missing"<p>
Query Handling<p>
Both GUI and CLI Interface<p>
Snippet Highlighting<p>
CranfieldMagic: Splits the combined Cranfield file into individual, clean documents<p>

**May Require 8.8.2 MemoryIndex Jar** <p>
https://mvnrepository.com/artifact/org.apache.lucene/lucene-memory/8.8.2

**How to Run** <p>
Compile: $ javac -cp "lib/*" -d bin src/edu/siena/csis225/projects25/*.java <p>
Run Main: $ java -cp "bin;lib/*" edu.siena.csis225.projects25.Main (launches GUI, append -text to command to run CLI) <p>
Flags that can be appended: <p>
-cran to clean index and search on cranfield data <p>
-parallel for parallel indexing <p>
-text for CLI <p>
                        
**To run CranfieldQAEvaluator, do the following:** <p>
Compile: $ javac -cp "lib/*" -d bin src/edu/siena/csis225/projects25/*.java <p>

Run QA Eval and Output to txt file (myResults.txt in this case): java -cp "bin;lib/*" edu.siena.csis225.projects25.Main -text -cran cranfield/cranfieldQA.txt | sed -e '1,/^Duration:.*ms$/d' > myResults.txt <p>

Run Stats on myResults.txt (or name of your txt file): $ java -cp "bin;lib/*" edu.siena.csis225.projects25.CranfieldQAStats myResults.txt cranfield/cranfieldGroundTruth.txt





