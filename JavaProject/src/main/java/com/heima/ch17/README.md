# 多线程（com.heima.ch17）

`package com.heima.ch17`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 17-01 | 进程、线程、并发与并行 | [ThreadConcept.java](ThreadConcept.java) |
| 17-02 | 继承Thread类创建线程 | [ExtendThread.java](ExtendThread.java) |
| 17-03 | 实现Runnable接口创建线程 | [RunnableDemo.java](RunnableDemo.java) |
| 17-04 | 线程的常用方法 | [ThreadMethods.java](ThreadMethods.java) |
| 17-05 | 线程安全问题与synchronized | [ThreadSafety.java](ThreadSafety.java) |
| 17-06 | 同步方法与Lock锁 | [SyncMethodLock.java](SyncMethodLock.java) |
| 17-07 | 死锁、wait与notify | [DeadlockWaitNotify.java](DeadlockWaitNotify.java) |
| 17-08 | 线程池 | [ThreadPoolDemo.java](ThreadPoolDemo.java) |
| 17-09 | 并发工具类与原子类 | [ConcurrentUtil.java](ConcurrentUtil.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：多线程售票与线程池调度

**场景**

用售票场景暴露线程安全问题，再用三种方式解决，最后把任务交给线程池统一调度并统计耗时。目标是把「共享状态 + 锁 + 线程池」串成一条线。

**任务**
1. 写一个不加锁的售票程序，用 3 个线程各卖 100 张票，复现超卖或重复售票，并说明为什么会出现。
2. 分别用 synchronized 同步代码块、同步方法、ReentrantLock 三种方式修复，对比吞吐与代码形态。
3. 用固定大小线程池提交 20 个任务，统计总耗时并与串行执行对比，说明线程池复用了什么。
4. 用 wait/notify 实现生产者消费者：一个线程放数据、一个线程取数据，队列满或空时正确阻塞。

**验收清单**
- [ ] 未加锁版本能稳定复现问题（多跑几次至少出现一次）。
- [ ] 三种加锁版本都恰好卖出 100 张票，不多不少。
- [ ] 线程池版本总耗时明显低于串行版本，且线程名能体现复用。
- [ ] 生产者消费者版本不出现死锁，程序能正常退出。

> 同一道题也写在 [ConcurrentUtil.java](ConcurrentUtil.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第17章-多线程.md](../../../../../../../黑马Java课程笔记/第17章-多线程.md) 的「本章综合练习」一节。
