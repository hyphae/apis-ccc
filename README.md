# apis-ccc

## Introduction
apis-ccc is client software for uploading information that is related to energy sharing to external services such as a server and downloading node configuration files from a server for operation and maintenance. It constructs clusters by using the functions of the Vert.x and Hazelcast frameworks to obtain information from apis-main that is running on multiple nodes that are connected via Ethernet or other communication lines. From the Grid Master in the cluster, it then obtains information on the hardware of each node, software information related to apis-main, and node-specific information. In that way, information on any node can be obtained. apis-ccc also has a function for downloading configuration files upon request from apis-main.


![キャプチャ](https://user-images.githubusercontent.com/71874910/95824505-5f35fc00-0d6a-11eb-8eb5-90fe79539d8a.PNG)


## Installation
Here is how to install apis-ccc individually.  
git, maven, groovy and JDK must be installed in advance.

```bash
$ git clone https://github.com/hyphae/apis-bom.git
$ cd apis-bom
$ mvn install
$ cd ../
$ git clone https://github.com/hyphae/apis-common.git
$ cd apis-common
$ mvn install
$ cd ../
$ git cone https://github.com/hyphae/apis-ccc.git
$ cd apis-ccc
$ mvn package
```

## Running
Here is how to run apis-ccc individually.  
```bash
$ cd exe
$ bash start.sh
```

## Stopping
Here is how to stop apis-ccc individually.  
```bash
$ cd exe
$ bash stop.sh
```

## Parameter Setting
Set the following file parameters in the exe folder as necessary.   
Refer to "Chapter 6, Configuration Files" in the [apis-ccc_specification](#anchor1) for more information.

&emsp;config.json   
&emsp;&emsp;&emsp;- communityId   &emsp;(default : oss_communityId)  
&emsp;&emsp;&emsp;- clusterId     &emsp;(default : oss_clusterId)  

&emsp;cluster.xml  
&emsp;&emsp;&emsp;- \<member\>  &emsp;(default : 127.0.0.1)  
&emsp;&emsp;&emsp;- \<interface\>  &emsp;(default : 127.0.0.1)  

&emsp;start.sh  
&emsp;&emsp;&emsp;-conf &emsp; (default : ./config.json)  
&emsp;&emsp;&emsp;-cluster-host &emsp; (default : 127.0.0.1)    



<a id="anchor1"></a>
## Documentation
&emsp;[apis-mian_ccc_specification(EN)](https://github.com/hyphae/apis-ccc/blob/main/doc/en/apis-ccc_specification_EN.md)  
&emsp;[apis-mian_ccc_specification(JP)](https://github.com/hyphae/apis-ccc/blob/main/doc/jp/apis-ccc_specification_JP.md)


## API Specification  

An example of creating an API specification using the Javadoc command is shown below.  
(For Ubuntu18.04)  
  
```bash  
$ export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/  
$ cd apis-ccc  
$ mvn javadoc:javadoc  
```  

The API specification is created in apis-ccc/target/site/apidocs/.  


## License
&emsp;[Apache License Version 2.0](https://github.com/hyphae/apis-ccc/blob/master/LICENSE)


## Notice
&emsp;[Notice](https://github.com/hyphae/apis-ccc/blob/master/NOTICE.md)
