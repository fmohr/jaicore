[![Build Status](https://travis-ci.org/fmohr/AILibs.svg?branch=dev)](https://travis-ci.org/fmohr/AILibs)


# AILibs
AILibs is a collection of Java libraries related to automated decision making. It currently consists of two building blocks. It is also home of the current version of the AutoML-tool [ML-Plan](https://github.com/fmohr/AILibs/tree/master/softwareconfiguration/mlplan).

* **JAICore** (Java AI Core) is a collection of projects with basic general purpose AI algorithms mainly in the area of logic reasoning, heuristic search, and machine learning
* **softwareconfiguration** is a collection of projects related to automatically configuring software systems. Here we also maintain the code for our AutoML flagship **[ML-Plan](https://github.com/fmohr/AILibs/tree/master/softwareconfiguration/mlplan)**

## Using AILibs in your project
You can resolve snapshots of this projects via a maven-dependency.
### Gradle 
First register our departements nexus as a maven repository:
```
repositories {
    mavenCentral()
	  maven { url "https://nexus.cs.upb.de/repository/sfb901-snapshots/" }
}
```
Then, you can either import the bundeled library via:
```
dependencies {
	 compile group: "de.upb.isys", name: "AILibs", version:"0.0.1-SNAPSHOT"
}
```
Or, the different artifacts individually e.g.
```
dependencies {
	 compile group: "de.upb.isys", name: "jaicore-ml", version:"0.0.1-SNAPSHOT"
}
```

## Setting up your IDE to work with AILibs
### Eclipse
Navigate to the folder where you cloned this repository and run
```
  ./gradlew eclipse
```
This automatically creates the eclipse project files and configures the dependencies among the projects.
Then open Eclipse and go to the import menu, e.g., in the package manager. Choose to import *Existing Projects into Workspace*, select the folder where you cloned the repository, and make sure to check the *Search for nested projects* option.


## AILibs JavaDoc API

### JAICore

* [JAICore:jaicore-basic](JAICore/jaicore-basic/docs/javadoc/)
* [JAICore:jaicore-concurrent](JAICore/jaicore-concurrent/docs/javadoc/)
* [JAICore:jaicore-ea](JAICore/jaicore-ea/docs/javadoc/)
* [JAICore:jaicore-experiments](JAICore/jaicore-experiments/docs/javadoc/)
* [JAICore:jaicore-graph](JAICore/jaicore-graph/docs/javadoc/)
* [JAICore:jaicore-graphvisualizer](JAICore/jaicore-graphvisualizer/docs/javadoc/)
* [JAICore:jaicore-logic](JAICore/jaicore-logic/docs/javadoc/)
* [JAICore:jaicore-math](JAICore/jaicore-math/docs/javadoc/)
* [JAICore:jaicore-ml](JAICore/jaicore-ml/docs/javadoc/)
* [JAICore:jaicore-planning](JAICore/jaicore-planning/docs/javadoc/)
* [JAICore:jaicore-processes](JAICore/jaicore-processes/docs/javadoc/)
* [JAICore:jaicore-search](JAICore/jaicore-search/docs/javadoc/)
* [JAICore:jaicore-services](JAICore/jaicore-services/docs/javadoc/)

### Software Configuration

* [HASCO](softwareconfiguration/hasco/docs/javadoc/)
* [ML-Plan](softwareconfiguration/mlplan/docs/javadoc/)
