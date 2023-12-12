# TADA: A Toolkit for Approximate Distributed Agreement

## Introduction

TADA (Toolkit for Approximate Distributed Agreement) is a Java toolkit implementing approximate distributed agreement functionalities. It grants the user the necessary infrastructure to _use_ and _implement_ Approximate Distributed Agreement algorithms, encapsulated in objects simulating the behaviour of Atomic Variables.

## Acknowledgments

This work is financed by the ERDF - European Regional Development Fund, through the Operational Programme for Competitiveness and Internationalisation - COMPETE 2020 Programme under the Portugal 2020 Partnership Agreement, and by National Funds through the FCT - Portuguese Foundation for Science and Technology, I.P. on the scope of the CMU Portugal Program within project AIDA, with reference POCI-01-0247-FEDER-045907

## How To Use

This toolkit's functionalities are detailed further in [TADA - A Toolkit for Approximate Distributed Agreement](https://link.springer.com/chapter/10.1007/978-3-031-35260-7_1). Results of the toolkit's performance and correctness are explored in said research paper. These results include:

1. Verifying the performance of the algorithms FCA, SynchDLPSW86 and AsyncDLPSW86 in a scenario with only correct processes;
2. Assessing the performance of the algorithms FCA, SynchDLPSW86 and AsyncDLPSW86 in the presence of faults;
3. Observing the behaviour of the FCA-based clock synchronization algorithm in the presence of one byzantine process.

These compose the functionality outcomes of the framework. To test any of these, we provide a JAR file containing the entirity of the library. It can be added to a project as a dependency to grant it access to all of the toolkit's features. Furthermore, the corresponding Javadocs are also provided.

### Setup

To run the toolkit, it is necessary to have an installation of [Java](https://www.java.com/en/download/), with the JDK and JRE version 19 or higher, along with [Maven](https://maven.apache.org/) to build the project and execute the tests with the provided scripts. You can download the source code of the toolkit in this repository. You must then unzip the downloaded file. You can build the project using Maven. From the main project directory, the same as the "pom.xml" file, execute the following command:

`$ mvn package`

The main test cases of the toolkit come packaged in `src.main.java.test`. To run these, it is necessary to create the proper directories to store the tests' output. To do so, you need to run the script `make_dirs.sh`, located in the `scripts` directory:

`$ ./scripts/make_dirs.sh`

### Execution

Each of the functionality outcomes can be tested with scripts found in the respective directory within the `scripts` folder (e.g. 1. can be tested using the scripts found in `scripts/F1/`. Three scripts have been provided for outcomes 1 and 2, and one for 3. Each script creates a number of processes equal to the argument passed to it, and tests the corresponding algorithm. For example, if you want to test *SynchDLPSW86* under byzantine conditions (outcome 2) with 8 processes, you execute the following command:

`$ ./scripts/F2/run_SynchDLPSW86.sh 8`

As per the definitions in the original papers, *FCA* and *SynchDLPSW86* both require at least 4 processes to guarantee correctness, and *AsyncDLPSW86* requires 6. The tests will not begin until at least that many processes are part of the communication group, so we caution you to provide at least those numbers. We recommend you run the tests for outcomes 1 and 2 using 8, 16 and 32 processes, to emulate the tests shown in the paper, as the tests for 64 and 128 processes will take a significant amount of time. The results for each test are stored in their respective directory in `output`. For the same example presented previously, the results would be stored in `output/F2/SynchDLPSW86/`.

The output of each test consists of a CSV file for each process, identified by the port of the process (as all tests are run in the localhost) and the total number of participants, containing an entry for each instance of consensus executed in the test. For the tests related to outcomes 1 and 2, the relevant column is the one pertaining to the execution time, `Texec`, to compare the performance of each algorithm. For the tests related to outcome 3, it is relevant to look at the columns `initialV` and `endingV`, in order to ascertain the difference between initial values and final values, along with the achieved precision. In particular, comparing the values in the latter between different processes is where the most insight can be gained, to find that, between correct processes, the values will differ by no more than the targeted precision. The single byzantine process in outcome 3's tests is the leader process, which is identified as such in the resulting CSV files.

### Playing with TADA

To experiment with the toolkit, by integrating it into other Java projects, one must simply add the JAR file provided in the main project directory, named _ApproximateConsensusToolkit-1.0.jar_, to the dependencies of said Java project, or as an external library. Questions related to the functionality described in the paper may be answered by visiting the documentation found in the `docs` directory. To get a firmer understanding of how one may start using the toolkit, we suggest starting by looking at the test classes implemented in the package `src/main/java/test/consensus`.

Documentation for the main Java classes that compose this project can be found in [](https://tarly127.github.io/TADA/).
