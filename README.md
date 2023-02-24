[TinyGit](#tinygit)

* [关于CS61B](关于cs61b)

* [Git原理](git原理)
  
  - [Git的仓库目录结构](git的仓库目录结构) 
  
  - [Git工作流](#git工作流)

* [TinyGit实现](#tinygit实现)

# TinyGit

A simple local Git implementation， based on CS61B-sp2021(UC, Berkeley)

本项目是简易版的Git实现，支持本地仓库功能和本地仓库之间的remote相关等20余种命令。项目实现框架遵循CS61B-sp2021中的proj2-gitlet，其中各类命令的实现也遵循课程项目文档的要求，并通过了课程的[gradescope](https://gradescope.com/)中所有测试。

个人做这个项目的目的是通过自己编码实践一些基础的Git命令实现，从而在未来工作中更好地理解真实Git的工作机制和底层存储模型。

# 关于CS61B

[课程官网]([Main | CS 61B Spring 2022 (berkeley.edu)](https://inst.eecs.berkeley.edu/~cs61b/sp22/)：CS61B是UC伯克利 CS61 系列的第二门课程，注重数据结构与算法的设计，同时让学生有机会接触上千行的工程代码，通过 Java 初步领会软件工程的思想。Gitlet是该课程的Project之一。

一些资料整理：

- [A Hacker’s Guide to Git](https://wildlyinaccurate.com/a-hackers-guide-to-git/)

- [Gitlet Persistence](https://link.zhihu.com/?target=https%3A//paper.dropbox.com/doc/Gitlet-Persistence-zEnTGJhtUMtGr8ILYhoab)

- Gitlet的前置实验：[Lab 6: Getting Started on Project 2](https://sp21.datastructur.es/materials/lab/lab6/lab6)

- 课程官网对于Gitlet实现的[实验说明文档]([Project 2: Gitlet | CS 61B Spring 2021](https://sp21.datastructur.es/materials/proj/proj2/proj2))，[规格](https://link.zhihu.com/?target=https%3A//sp21.datastructur.es/materials/proj/proj2/design.html)和[示例](https://link.zhihu.com/?target=https%3A//sp21.datastructur.es/materials/proj/proj2/capers-example)

        在实现Gitlet之前，当然需要了解真实的Git是如何进行数据存储和版本管理的。Gitlet和Git一样，所有数据组织模型均保存在文件根目录的隐藏文件夹下（以.开头，例如.git，.gitlet）。同时Gitlet对Git做了一些简化，目标是实现一个简易Git工具，支持常用本地命令（add\commit\merge\checkout\log\branch\find\reset等等），并为每个命令搭配了一系列的在线测试以供调试和验证实现的正确性。

# Git原理

## Git的仓库目录结构

![](https://cdn.nlark.com/yuque/0/2023/webp/29672299/1673355336263-8f08b3a2-7447-4f49-83e0-0c0a10c12b84.webp?x-oss-process=image%2Fresize%2Cw_551%2Climit_0)

## Git工作流

![](https://cdn.nlark.com/yuque/0/2023/png/29672299/1673089528666-407407df-b17f-4989-8fcb-832834637198.png)

![](https://cdn.nlark.com/yuque/0/2023/webp/29672299/1673355323635-cfa146e7-b05d-4f74-9950-b2ec352607e1.webp)

# Gitlet实现

Git 本质上是一个内容寻址的文件系统，而Gitlet是Git的简化版，采用Java语言实现，具体来说，二者的区别如下：

真正的Git区分并维护了以下几种不同的数据结构：

- Blobs: 保存的所有文件内容。由于 Gitlet 保存多个文件版本，一个文件可能对应于多个blob: 每个blob都在不同的commit中被跟踪。
- Trees: 将名称映射到 blobs 和其他Tree(子目录)的引用的目录结构。
- commits: 日志消息、其他元数据(提交日期、作者等)、对tree的引用和对父commit的引用的组合。存储库还维护 *branch heads*到commits的引用的映射，以便使某些重要的commit具有特定的tag。

Gitlet对Git进行了简化：

- 将tree合并进了commit
- 限制只能合并两个父引用
- 元数据主要由时间戳和日志消息组成。因此，commit将包括日志消息、时间戳、文件名到 blob 引用的映射、父commit引用和(对于merge)第二个父commit引用。

因此，Gitlet和Git在基本原理上很相似，例如，commits和Blob的关系如下：

![](https://cdn.nlark.com/yuque/0/2022/png/29672299/1663935190048-76f935a2-1362-479c-801c-ac858fc6589c.png)

[Gitlet命令实现规范描述：The Commands]([Project 2: Gitlet | CS 61B Spring 2021](https://sp21.datastructur.es/materials/proj/proj2/proj2#the-commands)

## 实现简概

主要通过Repository、Blob、Commit、Stage、LazySingleton、Utils几个类实现基本功能

### Repository.java

仓库方法类，只包含静态变量和方法，主要提供对.gilet目录下各类文件信息的操作命令函数实现，类本身无任何数据需要持久化到硬盘

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

### LazySingleton.java

通过双检锁实现懒汉式的单例模式类，完成对象的延迟加载

## Things to Avoid

1. 由于你可能会在文件中保存各种信息（如commits），你可能会想使用表面上很方便的文件系统操作（如列出目录）来顺序浏览所有的文件。请注意`File.list`和`File.listFiles`等方法会以未定义的顺序列出文件名。如果你用它们来实现`log`命令，你将会得到随机的结果。
2. Windows用户尤其应该注意，文件分隔符在Unix（或MacOS）上是`/`，在Windows上是`'\'`。因此，如果你在你的程序中通过将一些目录名和一个文件名串联在一起，再加上明确的`/`或`\`形成文件名，它在另一个系统上将不会起作用。Java提供了一个与系统相关的文件分隔符（`System.getProperty("file.separator")`），或者你可以使用多参数构造函数来构造`File`。
3. 在序列化时小心使用 HashMap！HashMap 中元素的顺序是不确定的。解决方案是使用 TreeMap，它总是有相同的遍历顺序。
