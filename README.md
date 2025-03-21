# bdt-metric-stitcher

## How to run

***环境基于docker搭建，请确保本地已正确安装docker( version >= 20.10.16), docker compose( versions >= 2.6.0)***

***脚本适配Mac/Linux/Unix，windows环境请执行相应的bat文件***

***(Mac/Wsl下可以使用普通用户执行，Linux/Unix下使用root或具备admin组的用户执行)***

- 拉取基础镜像，构建基础镜像
  ```shell
  cd ./docker-deps
  sh ./buildM3BaseImage.sh
  ```
- 准备基础环境：创建docker网络
    ```shell
    docker network create --driver=bridge --subnet=192.168.10.0/24 --ip-range=192.168.10.0/24 --gateway=192.168.10.1 devnws
    ```
- 准备基础环境：创建redis，mysql容器

  (***若环境容器已被其他项目启动，你需要在复用的mysql中手动创建项目的数据库&访问账密***)
  ```shell
  cd ./docker-deps
  docker compose up
  ```
- 打包制作application镜像并运行
  ```shell
  sh ./docker-startup.sh
  ```

以上步骤完成后，docker中将存在3个容器

```shell
root@page-Hd:~# docker ps
CONTAINER ID   IMAGE                   COMMAND                  CREATED       STATUS       PORTS                                                                                  NAMES
47f2f0fd31ee   metric-stitcher:1.0   "nohup java -jar -ag…"   4 days ago    Up 4 days      0.0.0.0:9000->9000/tcp, :::9000->9000/tcp                                              metric-management
92cde5c94642   mysql:8.0               "docker-entrypoint.s…"   4 weeks ago   Up 4 weeks   3306/tcp, 33060/tcp, 0.0.0.0:9918->9918/tcp, :::9918->9918/tcp                         m3sql
85b65be13391   redis:3.2               "docker-entrypoint.s…"   7 weeks ago   Up 6 weeks   0.0.0.0:6379->6379/tcp, :::6379->6379/tcp                                              m2redis
```

此时，程序已经在docker中运行起来，服务端口 8800，debug端口 8999， 日志目录 /tmp/logs/bdt-metric-stitcher

## How to use ...

### 关闭服务容器

```shell
sh ./docker-stop-java.sh
```

### 重新部署代码

```shell
sh ./docker-redeploy-java.sh
```

## How to remote Debug

***服务容器在启动时，映射了服务端口及远程debug端口，在idea中新建对应的remote jvm debug配置，源码指向本地根目录，端口设定为8999，启动debug后，idea将hit到容器的jvm进程中。***



