# apt-demo
 annotation processor demo

 1. 编译并打包安装到本地环境
    ```
    cd factory
    mvn clean install
    ```
2. 编译执行指定的main方法
    ```
    cd ../pizzaStore
    mvn -q package exec:java
    ```
