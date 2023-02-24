@[toc]

# TinyGit

A simple local Git implementation， based on CS61B(UC, Berkeley)

本项目是简易版的Git实现，支持本地仓库功能和本地仓库之间的remote相关命令。项目实现框架遵循CS61B中的proj2-gitlet，其中各类命令的实现也遵循课程项目文档的要求，并通过了课程的gradescope中的所有测试。

个人做这个项目的目的是通过自己用代码实践一些基础的Git命令实现，从而理解真实Git的工作机制和底层存储模型。



# 关于CS61B

一些资料整理

# Git原理

在实现TinyGit

## Git的仓库目录结构

![](https://cdn.nlark.com/yuque/0/2023/webp/29672299/1673355336263-8f08b3a2-7447-4f49-83e0-0c0a10c12b84.webp?x-oss-process=image%2Fresize%2Cw_551%2Climit_0)

## Git工作流

![](https://cdn.nlark.com/yuque/0/2023/png/29672299/1673089528666-407407df-b17f-4989-8fcb-832834637198.png)

![](https://cdn.nlark.com/yuque/0/2023/webp/29672299/1673355323635-cfa146e7-b05d-4f74-9950-b2ec352607e1.webp)

# TinyGit实现

Git 本质上是一个内容寻址的文件系统，而TinyGit是Git的简化版，采用Java实现，具体来说，二者的区别如下：

## 实现定义

主要通过Repository、Blob、Commit、Stage几个类实现基本功能

### Repository.java

仓库方法类，只包含静态变量和方法，主要提供对.gilet目录下各类文件信息的操作，类本身也没有任何数据需要持久化到硬盘

### 属性

- 各级目录路径
- 当前分支头

### Commit.java

负责实例化一个commit对象，本身包含一次提交的所有信息（提交说明、时间戳、blob的跟踪映射和父commit的引用等），并提供commit对象的数据访问、持久化操作等

### 属性

- hash标识符id
- 提交信息
- 提交时间
- 所有父亲Commit的引用指针列表
- 文件名到Blob Hashid的映射

### Blob.java

负责实例化一个blob对象，本身只包含文件的数据内容（在目前实现中还包含了文件名，真正的git中blob只包含文件元数据），并提供blob对象的数据访问、持久化操作等

### 属性

- hash标识符id
- 文件内容bytes数组
- 与Git相同，Blob文件只存储文件内容，而不包括文件名和路径等信息，从而使用纯粹的文件内容寻址，达成复用Blob对象的效果

### Stage.java

负责实例化暂存区的索引对象，本身只包含索引信息，不包含文件数据，并提供stage对象的数据访问、持久化操作等

### 属性

- 分为addstage和removestage两个暂存区，分别存储blob的相对路径文件名到blobid的映射
