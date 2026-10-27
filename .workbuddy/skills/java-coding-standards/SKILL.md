---
name: java-coding-standards
description: Java 后端编码规范（V1.2，阿里开发手册风格）。写或改 Java 代码、做 Code Review 时遵守：命名风格、常量与代码格式、集合与并发处理、控制语句、注释、异常处理、日志规约、安全编码（Web/数据/通讯/文件/内存）、错误码。本项目后端为 Spring Boot 3.4 + MyBatis-Plus + MySQL 8 单体工程。
---
# Java 开发规范（AI 友好版）

> 本文档基于桌面 `java开发规范.yaml` 整理，保留原始规范的全部规则，并优化为便于大模型理解、检索和引用的 Markdown 结构。
> - 版本：V1.2
> - 标签：`#Java` `#编码规范` `#安全规范` `#阿里巴巴开发手册风格`

---

## 使用说明

- **[强制]**：必须遵守，违反可能导致严重缺陷或安全事故。
- **[推荐]**：建议遵守，可显著提升代码质量与可维护性。
- **[参考]**：供参考，根据具体场景灵活采用。

---


# Java 开发规范

---

# 1. 简介

## 1.1 背景和目的

在一款软件的生命周期中，日常维护的时间成本将占整个生命周期的百分之八十。遵守代码规约，代码风格保持一致，提升代码的可读性，缩短维护所花费的时间。除此之外，统一的代码风格可以让新入手的人更顺畅的熟悉整个工程，不会因为风格迥异的代码造成不必要的困惑。 本文档的主要目的是希望规范Java开发的代码编码风格，尽量通过代码规范降低系统的维护成本。 本文档主要融合了阿里、Google等企业与团队的开发规范，结合项目实际情况以及个人的日常总结与经验教训。大家可以共同讨论，得出最符合当前状态的结论。

## 1.2 适用人群

本文档的适用人员范围包括涉及Java开发和设计的相关技术人员。

## 1.3 术语和缩略语

名词	解释【强制】	必须遵守的规范【推荐】	大部分情况需要遵守的规范【参考】	建议遵守

# 2. 编码规范

## 2.1 命名风格

1. [强制] **【强制】**代码中的命名均不能以下划线或美元符号开始，也不能以下划线或美元符号结束。
> **反例：**_name / __name / $name / name_ / name$ / name__
2. [强制] **【强制】**所有编程相关的命名严禁使用拼音与英文混合的方式，更不允许直接使用中文的方式。
> **说明：**正确的英文拼写和语法可以让阅读者易于理解，避免歧义。注意，纯拼音命名方式更要避免采用。
> **正例：**ali/alibaba/taobao/cainiao/aliyun/youku/hangzhou等国际通用的名称，可视同英文。
> **反例：**DaZhePromotion [打折]/getPingfenByName() [评分]/int 某变量 = 3
3. [强制] **【强制】**类名使用 UpperCamelCase 风格，但以下情形例外：DO / BO / DTO / VO / AO / PO / UID 等。
> **正例：**ForceCode/UserDO/HtmlDTO/XmlService/TcpUdpDeal/TaPromotion
> **反例：**forcecode/UserDo/HTMLDto/XMLService/TCPUDPDeal/TAPromotion
4. [强制] **【强制】**方法名、参数名、成员变量、局部变量都统一使用 lowerCamelCase 风格。
> **正例：**localValue/getHttpMessage()/inputUserId
5. [强制] **【强制】**常量命名全部大写，单词间用下划线隔开，力求语义表达完整清楚，不要嫌名字长。
> **正例：**MAX_STOCK_COUNT / CACHE_EXPIRED_TIME
> **反例：**MAX_COUNT / EXPIRED_TIME
6. [强制] **【强制】**抽象类命名使用 Abstract 或 Base 开头；异常类命名使用 Exception 结尾；测试类命名以它要测试的类的名称开始，以Test结尾。
7. [强制] **【强制】**类型与中括号紧挨相连来表示数组。
> **正例：**定义整形数组 int[] arrayDemo;
> **反例：**在 main 参数中，使用 String args[]来定义。
8. [强制] **【强制】**POJO 类中的任何布尔类型的变量，都不要加 is 前缀（包括is_），否则部分框架解析会引起序列化错误。
> **反例：**定义为基本数据类型 Boolean isDeleted 的属性，它的方法也是 isDeleted()，框架在反向解析的时候，“误以为”对应的属性名称是 deleted，导致属性获取不到，进而抛出异常。
9. [强制] **【强制】**包名统一使用小写，点分隔符之间有且仅有一个自然语义的英语单词。包名统一使用单数形式，但是类名如果有复数含义，类名可以使用复数形式。
> **正例：**应用工具类包名为 com.alibaba.ei.kunlun.aap.util、类名为 MessageUtils（此规则参考 spring 的框架结构）。
10. [强制] **【强制】**避免在子父类的成员变量之间、或者不同代码块的局部变量之间采用完全相同的命名，使可读性降低。
> **说明：**子类、父类成员变量名相同，即使是 public 类型的变量也是能够通过编译，而局部变量在同一方法内的不同代码块中同名也是合法的，但是要避免使用。对于非 setter/getter 的参数名称也要避免与成员变量名称相同。
> **反例：**
public class ConfusingName {public int stock;// 非 setter/getter 的参数名称，不允许与本类成员变量同名public void get(String alibaba) {if (condition) {final int money = 666;// ...}for (int i = 0; i < 10; i++) {// 在同一方法体中，不允许与其它代码块中的 money 命名相同
final int money = 15978;// ...}}}class Son extends ConfusingName {// 不允许与父类的成员变量名称相同public int stock;}
11. [强制] **【强制】**杜绝完全不规范的缩写，避免望文不知义。
> **反例：**AbstractClass“缩写”命名成 AbsClass；condition“缩写”命名成 condi，此类随意缩写严重降低了代码的可阅读性。
12. [推荐] **【推荐】**为了达到代码自解释的目标，任何自定义编程元素在命名时，使用尽量完整的单词组合来表达。
> **正例：**
在JDK中，对某个对象引用的volatile字段进行原子更新的类名为：AtomicReferenceFieldUpdater。
> **反例：**常见的方法内变量为 int a;的定义方式。
13. [推荐] **【推荐】**在常量与变量的命名时，表示类型的名词放在词尾，以提升辨识度。
> **正例：**startTime / workQueue / nameList / TERMINATED_THREAD_COUNT
> **反例：**startedAt / QueueOfWork / listName / COUNT_TERMINATED_THREAD
14. [推荐] **【推荐】**如果模块、接口、类、方法使用了设计模式，在命名时需体现出具体模式。
> **说明：**将设计模式体现在名字中，有利于阅读者快速理解架构设计理念。
> **正例：**
public class OrderFactory;public class LoginProxy;public class ResourceObserver;
15. [推荐] **【推荐】**接口类中的方法和属性不要加任何修饰符号（public 也不要加），保持代码的简洁性，并加上有效的 Javadoc 注释。尽量不要在接口里定义变量，如果一定要定义变量，确定与接口方法相关，并且是整个应用的基础常量。
> **正例：**
接口方法签名 void commit();接口基础常量 String COMPANY = "alibaba";
> **反例：**
接口方法定义 public abstract void f();
> **说明：**JDK8 中接口允许有默认实现，那么这个 default 方法，是对所有实现类都有价值的默认实现。
16.接口和实现类的命名有两套规则：1)【强制】对于 Service 和 DAO 类，基于 SOA 的理念，暴露出来的服务一定是接口，内部的实现类用 Impl 的后缀与接口区别。
> **正例：**CacheServiceImpl 实现 CacheService 接口。
2)【推荐】如果是形容能力的接口名称，取对应的形容词为接口名（通常是–able 的形容词）。
> **正例：**AbstractTranslator 实现 Translatable 接口。
17. [参考] **【参考】**枚举类名带上 Enum 后缀，枚举成员名称需要全大写，单词间用下划线隔开。
> **说明：**枚举其实就是特殊的常量类，且构造方法被默认强制是私有。
> **正例：**枚举名字为 ProcessStatusEnum 的成员名称：SUCCESS / UNKNOWN_REASON。
18. [参考] **【参考】**各层命名规约：
1)Service/DAO 层方法命名规约，采用动宾关系：①　获取单个对象的方法用 get 做前缀。 ②　获取多个对象的方法用 get 做前缀，如getObjectLists。 ③　获取统计值的方法用 get 做前缀。 ④　插入的方法用 save/insert 做前缀。 ⑤　删除的方法用 remove/delete 做前缀。 ⑥　修改的方法用 update 做前缀。 2)领域模型命名规约：①　数据对象：xxxDO，xxx 即为数据表名。
②　数据传输对象：xxxDTO，xxx 为业务领域相关的名称。 ③　展示对象：xxxVO，xxx 一般为网页名称。 ④　POJO 是 DO/DTO/BO/VO 的统称，禁止命名成 xxxPOJO。

## 2.2 常量定义

【强制】不允许任何魔法值（即未经预先定义的常量）直接出现在代码中。
> **反例：**
//本例中同学 A 定义了缓存的 key，然后缓存提取的同学 B 使用了 Id#taobao 来提取，少了下划线，导致故障。 String key = "Id#taobao_" + tradeId;cache.put(key, value);
1. [强制] **【强制】**在long或者Long赋值时，数值后使用大写的L，不能是小写的l，小写容易跟数字混淆，造成误解。
2. [强制] **【强制】**在long或者Long赋值时，数值后使用大写的L，不能是小写的l，小写容易跟数字混淆，造成误解。
> **说明：**Long a = 2l; 写的是数字的 21，还是 Long 型的 2。
3. [推荐] **【推荐】**不要使用一个常量类维护所有常量，要按常量功能进行归类，分开维护。
> **说明：**大而全的常量类，杂乱无章，使用查找功能才能定位到修改的常量，不利于理解，也不利于维护。
> **正例：**缓存相关常量放在类 CacheConsts 下；系统配置相关常量放在类 ConfigConsts 下。
4. [推荐] **【推荐】**常量的复用层次有五层：跨应用共享常量、应用内共享常量、子工程内共享常量、包内共享常量、类内共享常量。
1)跨应用共享常量：放置在二方库中，通常是 client.jar 中的 constant 目录下。 2)应用内共享常量：放置在一方库中，通常是子模块中的 constant 目录下。
> **反例：**易懂变量也要统一定义成应用内共享常量，两位工程师在两个类中分别定义了“YES”的变量：
类 A 中：public static final String YES = "yes";类 B 中：public static final String YES = "y";A.YES.equals(B.YES)，预期是 true，但实际返回为 false，导致线上问题。 3)子工程内部共享常量：即在当前子工程的 constant 目录下。 4)包内共享常量：即在当前包下单独的 constant 目录下。
5)类内共享常量：直接在类内部 private static final 定义。
5. [推荐] **【推荐】**如果变量值仅在一个固定范围内变化用 enum 类型来定义。
> **说明：**如果存在名称之外的延伸属性应使用 enum 类型，下面正例中的数字就是延伸信息，表示一年中的第几个季节。
> **正例：**
public enum SeasonEnum {SPRING(1), SUMMER(2), AUTUMN(3), WINTER(4);private int seq;SeasonEnum(int seq) {this.seq = seq;}public int getSeq() {return seq;}}

## 2.3 代码格式

1. [强制] **【强制】**如果是大括号内为空，则简洁地写成{}即可，大括号中间无需换行和空格；如果是非空代码块则:
1)左大括号前不换行。 2)左大括号后换行。 3)右大括号前换行。 4)右大括号后还有 else 等代码则不换行；表示终止的右大括号后必须换行。
2. [强制] **【强制】**左小括号和右边相邻字符之间不出现空格；右小括号和左边相邻字符之间也不出现空格；而左大括号前需要加空格。详见第 5 条下方正例提示。
> **反例：**if (空格 a == b 空格)
3. [强制] **【强制】**if/for/while/switch/do 等保留字与括号之间都必须加空格。
4. [强制] **【强制】**任何二目、三目运算符的左右两边都需要加一个空格。
> **说明：**包括赋值运算符=、逻辑运算符&&、加减乘除符号等。
5. [强制] **【强制】**采用 4 个空格缩进，禁止使用 tab 字符。
> **说明：**如果使用 tab 缩进，必须设置 1 个 tab 为 4 个空格。IDEA 设置 tab 为 4 个空格时，请勿勾选 Use tab character；而在 eclipse 中，必须勾选 insert spaces for tabs。
> **正例：** （涉及 1-5 点）
public static void main(String[] args) {// 缩进 4 个空格String say = "hello";// 运算符的左右必须有一个空格int flag = 0;// 关键词 if 与括号之间必须有一个空格，括号内的 f 与左括号，0 与右括号不需要空格 if (flag == 0) {System.out.println(say);}// 左大括号前加空格且不换行；左大括号后换行
if (flag == 1) {System.out.println("world");// 右大括号前换行，右大括号后有 else，不用换行} else {System.out.println("ok");// 在右大括号后直接结束，则必须换行}}
6. [强制] **【强制】**注释的双斜线与注释内容之间有且仅有一个空格。
> **正例：**
// 这是示例注释，请注意在双斜线之后有一个空格String commentString = new String();
7. [强制] **【强制】**在进行类型强制转换时，右括号与强制转换值之间不需要任何空格隔开。
> **正例：**
long first = 1000000000000L;int second = (int)first + 2;
8. [强制] **【强制】**单行字符数限制不超过 120 个，超出需要换行，换行时遵循如下原则：
第二行相对第一行缩进 4 个空格，从第三行开始，不再继续缩进，参考示例。 运算符与下文一起换行。 方法调用的点符号与下文一起换行。 方法调用中的多个参数需要换行时，在逗号后进行。 在括号前不要换行，见反例。
> **正例：**
StringBuilder sb = new StringBuilder();// 超过 120 个字符的情况下，换行缩进 4 个空格，并且方法前的点号一起换行sb.append("zi").append("xin")....append("huang")....append("huang")....append("huang");
> **反例：**
StringBuilder sb = new StringBuilder();// 超过 120 个字符的情况下，不要在括号前换行sb.append("you").append("are")...append("lucky");// 参数很多的方法调用可能超过 120 个字符，逗号后才是换行处method(args1, args2, args3, ..., argsX);
9. [强制] **【强制】**方法参数在定义和传入时，多个参数逗号后边必须加空格。
> **正例：**下例中实参的 args1，后边必须要有一个空格。
method(args1, args2, args3);
10. [强制] **【强制】**IDE的text file encoding设置为 UTF-8; IDE 中文件的换行符使用 Unix 格式，不要使用 Windows 格式。
11. [参考] **【参考】**单个方法的总行数不超过 80 行。
> **说明：**除注释之外的方法签名、左右大括号、方法内代码、空行、回车及任何不可见字符的总行数不超过80行。
> **正例：**代码逻辑分清红花和绿叶，个性和共性，绿叶逻辑单独出来成为额外方法，使主干代码更加清晰；共性逻辑抽取成为共性方法，便于复用和维护。
12. [推荐] **【推荐】**没有必要增加若干空格来使变量的赋值等号与上一行对应位置的等号对齐。
> **正例：**
int one = 1;long two = 2L;float three = 3F;StringBuilder sb = new StringBuilder();
> **说明：**增加 sb 这个变量，如果需要对齐，则给 one、two、three 都要增加几个空格，在变量比较多的情况下，是非常累赘的事情。
13. [推荐] **【推荐】**不同逻辑、不同语义、不同业务的代码之间插入一个空行分隔开来以提升可读性。
> **说明：**任何情形，没有必要插入多个空行进行隔开。
2.4. OOP规约
1. [强制] **【强制】**避免通过一个类的对象引用访问此类的静态变量或静态方法，无谓增加编译器解析成本，直接用类名来访问即可。
> **说明：**使用类名调用静态的类成员，而不是具体某个对象或表达式。静态变量是类实例变量，可以用类名.静态属性直接访问，new一个对象需要分配内存空间，无端增加花费成本。
2. [强制] **【强制】**所有的覆写方法，必须加@Override 注解。
> **说明：**getObject()与 get0bject()的问题。一个是字母的 O，一个是数字的 0，加@Override 可以准确判断是否覆盖成功。另外，如果在抽象类中对方法签名进行修改，其实现类会马上编译报错。加上注解能增加代码的可读性，看到标签就知道这是从父类重写的方法，在调用时也将调用重写后的方法。并且使用@Override可以准确判断是否覆盖成功。
3. [推荐] **【推荐】**相同参数类型，相同业务含义，才可以使用 Java 的可变参数，避免使用 Object。
> **说明：**可变参数必须放置在参数列表的最后。（提倡尽量不用可变参数编程）
> **正例：**public List<User> listUsers(String type, Long... ids) {...}
5. [推荐] **【推荐】**不能使用过时的类或方法。
> **说明：**java.net.URLDecoder 中的方法 decode(String encodeStr) 这个方法已经过时，应该使用双参数 decode(String source, String encode)。接口提供方既然明确是过时接口，那么有义务同时提供新的接口；作为调用方来说，有义务去考证过时方法的新实现是什么。
6. [强制] **【强制】**Object的equals方法容易抛空指针异常，应使用常量或确定有值的对象来调用 equals。
> **正例：**"test".equals(object);
> **反例：**object.equals("test");
> **说明：**推荐使用 java.util.Objects#equals（JDK7 引入的工具类）。
7. [强制] **【强制】**所有整型包装类对象之间值的比较，全部使用 equals方法比较。
> **说明：**对于 Integer var = ? 在-128至127之间的赋值，Integer对象是在 IntegerCache.cache 产生，会复用已有对象，这个区间内的 Integer 值可以直接使用==进行判断，但是这个区间之外的所有数据，都会在堆上产生，并不会复用已有对象，这是一个大坑，推荐使用 equals 方法进行判断。
8. [强制] **【强制】**任何货币金额，均以最小货币单位且整型类型来进行存储。
> **说明：**比如说人民币的最小单位是分，那假设一个商品的价格是1元钱，那就存到数据库的 price 字段，字段类型是 int 或者 bigint，值为 100，单位是分，也就是100分。
9. [强制] **【强制】**浮点数之间的等值判断，基本数据类型不能用==来比较，包装数据类型不能用  equals来判断。
> **说明：**浮点数采用“尾数+阶码”的编码方式，类似于科学计数法的“有效数字+指数”的表示方式。二进制无法精确表示大部分的十进制小数。
> **反例：**
float a = 1.0f - 0.9f;float b = 0.9f - 0.8f;if (a == b) {// 预期进入此代码快，执行其它业务逻辑// 但事实上 a==b 的结果为 false}Float x = Float.valueOf(a);Float y = Float.valueOf(b);if (x.equals(y)) {// 预期进入此代码快，执行其它业务逻辑// 但事实上 equals 的结果为 false
}
> **正例：**
指定一个误差范围，两个浮点数的差值在此范围之内，则认为是相等的。 float a = 1.0f - 0.9f;float b = 0.9f - 0.8f;float diff = 1e-6f;if (Math.abs(a - b) < diff) {System.out.println("true");}使用 BigDecimal 来定义值，再进行浮点数的运算操作。 BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("0.9");BigDecimal c = new BigDecimal("0.8");BigDecimal x = a.subtract(b);BigDecimal y = b.subtract(c);if (x.equals(y)) {System.out.println("true");}
10. [强制] **【强制】**定义数据对象 DO 类时，属性类型要与数据库字段类型相匹配。
> **正例：**数据库字段的 bigint 必须与类属性的 Long 类型相对应。
> **反例：**某个案例的数据库表 id 字段定义类型 bigint unsigned，实际类对象属性为 Integer，随着 id 越来越大，超过 Integer 的表示范围而溢出成为负数。
11. [强制] **【强制】**禁止使用构造方法 BigDecimal(double)的方式把 double 值转化为 BigDecimal 对象。
> **说明：**BigDecimal(double)存在精度损失风险，在精确计算或值比较的场景中可能会导致业务逻辑异常。
如：BigDecimal g = new BigDecimal(0.1f); 实际的存储值为：0.10000000149
> **正例：**优先推荐入参为 String 的构造方法，或使用 BigDecimal 的 valueOf 方法，此方法内部其实执行了 Double 的 toString，而 Double 的 toString 按 double 的实际能表达的精度对尾数进行了截断。
BigDecimal recommend1 = new BigDecimal("0.1");BigDecimal recommend2 = BigDecimal.valueOf(0.1);12.关于基本数据类型与包装数据类型的使用标准如下：【推荐】所有的 POJO 类属性必须使用包装数据类型。 【推荐】RPC 方法的返回值和参数必须使用包装数据类型。 【推荐】所有的局部变量使用基本数据类型。
13. [推荐] **【推荐】**定义 DO/DTO/VO 等 POJO 类时，不要设定任何属性默认值。
> **说明：**POJO 类属性没有初值是提醒使用者在需要使用时，必须自己显式地进行赋值，任何 NPE 问题，或者入库检查，都由使用者来保证。
> **正例：**数据库的查询结果可能是 null，因为自动拆箱，用基本数据类型接收有 NPE 风险。
> **反例：**某业务的交易报表上显示成交总额涨跌情况，即正负 x%，x 为基本数据类型，调用的 RPC 服务，调用不成功时，返回的是默认值，页面显示为 0%，这是不合理的，应该显示成中划线-。所以包装数据类型的 null 值，能够表示额外的信息，如：远程调用失败，异常退出。
> **反例：**POJO 类的 createTime 默认值为 new Date()，但是这个属性在数据提取时并没有置入具体值，在更新其它字段时又附带更新了此字段，导致创建时间被修改成当前时间。
14. [强制] **【强制】**序列化类新增属性时，请不要修改 serialVersionUID 字段，避免反序列失败；如果完全不兼容升级，避免反序列化混乱，那么请修改 serialVersionUID 值。
> **说明：**注意 serialVersionUID 不一致会抛出序列化运行时异常。Java的序列化机制是通过在运行时判断类的serialVersionUID来验证版本一致性的。在进行反序列化时，JVM会把传来的字节流中的serialVersionUID与本地相应实体（类）的serialVersionUID进行比较，如果相同就认为是一致的，可以进行反序列化，否则就会出现序列化版本不一致的异常。当实现java.io.Serializable接口的实体（类）没有显式地定义一个名为serialVersionUID，类型为long的变量时，Java序列化机制会根据编译的class自动生成一个serialVersionUID作序列化版本比较用，这种情况下，只有同一次编译生成的class才会生成相同的serialVersionUID 。
15. [强制] **【强制】**构造方法里面禁止加入任何业务逻辑，如果有初始化逻辑，请放在 init 方法中。
> **说明：**构造方法一般是类的初始化方法，如果加入业务逻辑代码将非常影响可读性，如果有必须的初始化操作方法可以创建init方法进行操作。
16. [强制] **【强制】**POJO 类必须写 toString 方法。使用 IDE 中的工具：source> generate toString 时，如果继承了另一个 POJO 类，注意在前面加一下 super.toString。
> **说明：**在方法执行抛出异常时，可以直接调用 POJO 的 toString()方法打印其属性值，便于排查问题。
17. [推荐] **【推荐】**禁止在 POJO 类中，同时存在对应属性 xxx 的 isXxx()和 getXxx()方法。
> **说明：**框架在调用属性 xxx 的提取方法时，并不能确定哪个方法一定是被优先调用到，神坑之一。
18. [推荐] **【推荐】**使用索引访问用 String 的 split 方法得到的数组时，需做最后一个分隔符后有无内容的检查，否则会有抛 IndexOutOfBoundsException 的风险。
> **说明：**
String str = "a,b,c,,";String[] ary = str.split(",");// 预期大于 3，结果是 3System.out.println(ary.length);
19. [推荐] **【推荐】**当一个类有多个构造方法，或者多个同名方法，这些方法应该按顺序放置在一起，便于阅读，此条规则优先于下一条。
20. [推荐] **【推荐】** 类内方法定义的顺序依次是：公有方法或保护方法 > 私有方法 > getter / setter 方法。
> **说明：**公有方法是类的调用者和维护者最关心的方法，首屏展示最好；保护方法虽然只是子类关心，也可能是“模板设计模式”下的核心方法；而私有方法外部一般不需要特别关心，是一个黑盒实现；因为承载的信息价值较低，所有Service和DAO的getter/setter方法放在类体最后。
21. [推荐] **【推荐】**setter方法中，参数名称与类成员变量名称一致，this.成员名 = 参数名。在getter/setter 方法中，不要增加业务逻辑，增加排查问题的难度。
> **反例：**
public Integer getData () {if (condition) {return this.data + 100;} else {return this.data - 100;}}
22. [推荐] **【推荐】**循环体内，字符串的连接方式，使用 StringBuilder 的 append 方法进行扩展。
> **说明：**下例中，反编译出的字节码文件显示每次循环都会 new 出一个 StringBuilder 对象，然后进行 append 操作，最后通过 toString 方法返回 String 对象，造成内存资源浪费。
> **反例：**
String str = "start";for (int i = 0; i < 100; i++) {str = str + "hello";}
23. [推荐] **【推荐】**final 可以声明类、成员变量、方法、以及本地变量，下列情况使用 final 关键字：
不允许被继承的类，如：String 类。 不允许修改引用的域对象，如：POJO 类的域变量。 不允许被覆写的方法，如：POJO 类的 setter 方法。 不允许运行过程中重新赋值的局部变量。 避免上下文重复使用一个变量，使用 final 可以强制重新定义一个变量，方便更好地进行重构。
> **说明：**对final 在Java中是一个保留的关键字，可以声明成员变量、方法、类以及本地变量。一旦引用声明作final，将不能改变这个引用了，编译器会检查代码，如果试图将变量再次初始化的话，编译器会报编译错误。
24. [推荐] **【推荐】**慎用 Object 的 clone 方法来拷贝对象。
> **说明：**对象 clone 方法默认是浅拷贝，若想实现深拷贝需覆写 clone 方法实现域对象的深度遍历式拷贝。
25. [推荐] **【推荐】**类成员与方法访问控制从严：
1)如果不允许外部直接通过 new 来创建对象，那么构造方法必须是 private。 2)工具类不允许有 public 或 default 构造方法。 3)类非static成员变量并且与子类共享，必须是 protected。 4)类非 static成员变量并且仅在本类使用，必须是 private。 5)类static成员变量如果仅在本类使用，必须是 private。 6)若是 static 成员变量，考虑是否为 final。
7)类成员方法只供类内部调用，必须是 private。 8)类成员方法只对继承类公开，那么限制为 protected。
> **说明：**任何类、方法、参数、变量，严控访问范围。过于宽泛的访问范围，不利于模块解耦。思考：如果是一个 private 的方法，想删除就删除，可是一个 public 的 service 成员方法或成员变量，删除一下，不得手心冒点汗吗？变量像自己的小孩，尽量在自己的视线内，变量作用域太大，无限制的到处跑，那么你会担心的。
2.5. 日期时间
1. [推荐] **【推荐】**日期格式化时，传入 pattern 中表示年份统一使用小写的 y。
> **说明：**日期格式化时，yyyy 表示当天所在的年，而大写的 YYYY 代表是 week in which year（JDK7 之后引入的概念），意思是当天所在的周属于的年份，一周从周日开始，周六结束，只要本周跨年，返回的 YYYY 就是下一年。
> **正例：**表示日期和时间的格式如下所示：
new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
2. [强制] **【强制】**在日期格式中分清楚大写的 M 和小写的 m，大写的 H 和小写的 h 分别指代的意义。
> **说明：**日期格式中的这两对字母表意如下：
1)表示月份是大写的 M；2)表示分钟则是小写的 m；3)24 小时制的是大写的 H；4)12 小时制的则是小写的 h。
3. [强制] **【强制】**获取当前毫秒数：System.currentTimeMillis(); 而不是 new Date().getTime()。
> **说明：**如果想获取更加精确的纳秒级时间值，使用 System.nanoTime 的方式。在 JDK8 中，针对统计时间等场景，推荐使用 Instant 类。为什么不让使用new Date().getTime()；因为Date的源码中也是调用的System.currentTimeMillis()方法，所以直接使用System.currentTimeMillis()会比new一个Date对象性能高。
4. [强制] **【强制】**不允许在程序任何地方中使用：
1)java.sql.Date2)java.sql.Time3)java.sql.Timestamp
> **说明：**第 1 个不记录时间，getHours()抛出异常；第 2 个不记录日期，getYear()抛出异常；第 3 个在构造方法 super((time/1000)*1000)，fastTime 和 nanos 分开存储秒和纳秒信息。
> **反例：** java.util.Date.after(Date)进行时间比较时，当入参是 java.sql.Timestamp 时，会触发JDK BUG(JDK9 已修复)，可能导致比较时的意外结果。
5. [强制] **【强制】**不要在程序中写死一年为 365 天，避免在公历闰年时出现日期转换错误或程序逻辑错误。
> **正例：**
// 获取今年的天数int daysOfThisYear = LocalDate.now().lengthOfYear();// 获取指定某年的天数LocalDate.of(2011, 1, 1).lengthOfYear();
> **反例：**
// 第一种情况：在闰年 366 天时，出现数组越界异常int[] dayArray = new int[365];// 第二种情况：一年有效期的会员制，今年 1 月 26 日注册，硬编码 365 返回的却是 1 月 25 日Calendar calendar = Calendar.getInstance();calendar.set(2020, 1, 26);calendar.add(Calendar.DATE, 365);
6. [推荐] **【推荐】**避免公历闰年2月问题。闰年的2月份有29天，一年后的那一天不可能是2月29日。
7. [推荐] **【推荐】**使用枚举值来指代月份。如果使用数字，注意 Date，Calendar 等日期相关类的月份
month 取值在 0-11 之间。
> **说明：**参考 JDK 原生注释，Month value is 0-based. e.g., 0 for January.
> **正例：** Calendar.JANUARY，Calendar.FEBRUARY，Calendar.MARCH 等来指代相应月份来进行传参或比较。

## 2.5 HTTP 接口方法规约

1. [强制] **【强制】**对外 HTTP 接口统一使用 `@PostMapping`（POST）方法，禁止使用 `@GetMapping`（GET）定义业务接口。
> **说明：**GET 请求参数暴露在 URL 中（浏览器历史、代理日志、访问日志），易造成敏感信息泄漏，且受 URL 长度限制；统一使用 POST 便于参数统一校验与统一拦截（日志切面、数据源切面等）。
> **正例：**
> `@PostMapping("/order/list")`
> **反例：**
> `@GetMapping("/order/list")`
2. [推荐] **【推荐】**查询、分页等"读"接口同样使用 POST + JSON body 传参，保持接口风格统一。

## 2.6 集合处理

1. [推荐] **【推荐】**关于 hashCode 和 equals 的处理，遵循如下规则：
只要重写 equals，就必须重写 hashCode。因为 Set 存储的是不重复的对象，依据 hashCode 和 equals 进行判断，所以 Set 存储的对象必须重写这两个方法。如果自定义对象作为 Map 的键，那么必须覆写 hashCode 和 equals。
> **说明：**String 因为重写了 hashCode 和 equals 方法，所以我们可以愉快地使用 String 对象作为 key 来使用。
2. [强制] **【强制】**判断所有集合内部的元素是否为空，使用 isEmpty()方法，而不是 size()==0 的方式。
> **说明：**前者的时间复杂度为 O(1)，而且可读性更好。
> **正例：**
Map<String, Object> map = new HashMap<>();if(map.isEmpty()) {System.out.println("no element in this map.");}
3. [强制] **【强制】**在使用 java.util.stream.Collectors 类的 toMap()方法转为 Map 集合时，一定要使用含有参数类型为 BinaryOperator，参数名为 mergeFunction 的方法，否则当出现相同 key值时会抛出 IllegalStateException 异常。
> **说明：**参数 mergeFunction 的作用是当出现 key 重复时，自定义对 value 的处理策略。
> **正例：**
List<Pair<String, Double>> pairArrayList = new ArrayList<>(3);pairArrayList.add(new Pair<>("version", 6.19));pairArrayList.add(new Pair<>("version", 10.24));pairArrayList.add(new Pair<>("version", 13.14));
Map<String, Double> map = pairArrayList.stream().collect(// 生成的map集合中只有一个键值对：{version=13.14} Collectors.toMap(Pair::getKey, Pair::getValue, (v1, v2) -> v2));
> **反例：**
String[] departments = new String[] {"iERP", "iERP", "EIBU"}; // 抛出 IllegalStateException 异常Map<Integer, String> map = Arrays.stream(departments).collect(Collectors.toMap(String::hashCode, str -> str));
4. [强制] **【强制】**在使用 java.util.stream.Collectors类的 toMap()方法转为 Map 集合时，一定要注意当 value 为 null 时会抛 NPE 异常。
> **说明：**在 java.util.HashMap 的 merge 方法里会进行如下的判断：
if (value == null || remappingFunction == null)throw new NullPointerException();
> **反例：**
List<Pair<String, Double>> pairArrayList = new ArrayList<>(2);pairArrayList.add(new Pair<>("version1", 4.22));pairArrayList.add(new Pair<>("version2", null));Map<String, Double> map = pairArrayList.stream().collect(
// 抛出 NullPointerException 异常Collectors.toMap(Pair::getKey, Pair::getValue, (v1, v2) -> v2));
5. [强制] **【强制】**ArrayList 的 subList 结果不可强转成 ArrayList，则会抛出常：java.util.RandomAccessSubList cannot be cast to java.util.ArrayList。
> **说明：**subList 返回的是 ArrayList 的内部类 SubList，并不是 ArrayList 而是 ArrayList 的一个视图，对于 SubList 子列表的所有操作最终会反映到原列表上。
6. [强制] **【强制】**使用 Map 的方法 keySet()/values()/entrySet()返回集合对象时，不可以对其进行添加元素操作，否则会抛出 UnsupportedOperationException 异常。
【强制】Collections 类返回的对象，如：emptyList()/singletonList()等都是 immutable list，不可对其进行添加或者删除元素的操作。
> **反例：**如果查询无结果，返回 Collections.emptyList()空集合对象，调用方一旦进行了添加元素的操作，就会触发 UnsupportedOperationException 异常。
7. [强制] **【强制】**在 subList 场景中，高度注意对父集合元素的增加或删除，均会导致子列表的遍历、增加、删除产生 ConcurrentModificationException 异常。
8. [强制] **【强制】**使用集合转数组的方法，必须使用集合的 toArray(T[] array)，传入的是类型完全一致、长度为 0 的空数组。
> **反例：**直接使用 toArray 无参方法存在问题，此方法返回值只能是 Object[]类，若强转其它类型数组将出现 ClassCastException 错误。
> **正例：**
List<String> list = new ArrayList<>(2);list.add("guan");list.add("bao");String[] array = list.toArray(new String[0]);
> **说明：**使用 toArray 带参方法，数组空间大小的 length，
1)等于0，动态创建与size相同的数组，性能最好。 2)大于0但小于size，重新创建大小等于size的数组，增加GC负担。 3)等于size，在高并发情况下，数组创建完成之后，size 正在变大的情况下，负面影响与2相同。 4)大于size，空间浪费，且在size处插入null值，存在NPE隐患。
9. [强制] **【强制】**在使用 Collection 接口任何实现类的 addAll()方法时，都要对输入的集合参数进行 NPE 判断。
> **说明：**在 ArrayList#addAll 方法的第一行代码即 Object[] a = c.toArray(); 其中 c 为输入集合参数，如果为 null，则直接抛出异常。
10. [强制] **【强制】**使用工具类 Arrays.asList()把数组转换成集合时，不能使用其修改集合相关的方法，它的 add/remove/clear 方法会抛出 UnsupportedOperationException 异常。
> **说明：**asList 的返回对象是一个 Arrays 内部类，并没有实现集合的修改方法。Arrays.asList 体现的是适配器模式，只是转换接口，后台的数据仍是数组。
String[] str = new String[] { "yang", "hao" };List list = Arrays.asList(str);第一种情况：list.add("x"); 运行时异常。 第二种情况：str[0] = "changed"; 也会随之修改，反之亦然。
11. [推荐] **【推荐】**泛型通配符<? extends T>来接收返回的数据，此写法的泛型集合不能使用 add 方法，而<? super T>不能使用 get 方法，两者在接口调用赋值的场景中容易出错。
> **说明：**扩展说一下 PECS(Producer Extends Consumer Super)原则：第一、频繁往外读取内容的，适合用 <? extends T>。第二、经常往里插入的，适合用<? super T>
12. [推荐] **【推荐】**在无泛型限制定义的集合赋值给泛型限制的集合时，在使用集合元素时，需要进行 instanceof 判断，避免抛出 ClassCastException 异常。
> **说明：**毕竟泛型是在 JDK5 后才出现，考虑到向前兼容，编译器是允许非泛型集合与泛型集合互相赋值。
> **反例：**
List<String> generics = null;List notGenerics = new ArrayList(10);notGenerics.add(new Object());notGenerics.add(new Integer(1));generics = notGenerics;// 此处抛出 ClassCastException 异常String string = generics.get(0);
13. [强制] **【强制】**不要在 foreach 循环里进行元素的 remove/add 操作。remove 元素请使用 Iterator 方式，如果并发操作，需要对 Iterator 对象加锁。
> **正例：**
List<String> list = new ArrayList<>();list.add("1");list.add("2");Iterator<String> iterator = list.iterator();while (iterator.hasNext()) {String item = iterator.next();if (删除元素的条件) {iterator.remove();
}}
> **反例：**
for (String item : list) {if ("1".equals(item)) {list.remove(item);}}
> **说明：**以上代码的执行结果肯定会出乎大家的意料，那么试一下把“1”换成“2”，会是同样的结果吗？
14. [强制] **【强制】**在 JDK7 版本及以上，Comparator 实现类要满足如下三个条件，不然 Arrays.sort，Collections.sort 会抛 IllegalArgumentException 异常。
> **说明：**三个条件如下：
1)x，y 的比较结果和 y，x 的比较结果相反；2)x>y，y>z，则 x>z；3)x=y，则 x，z 比较结果和 y，z 比较结果相同。
> **反例：**下例中没有处理相等的情况，交换两个对象判断结果并不互反，不符合第一个条件，在实际使用中可能会出现异常。
new Comparator<Student>() {@Overridepublic int compare(Student o1, Student o2) {return o1.getId() > o2.getId() ? 1 : -1;}};
15. [推荐] **【推荐】**集合泛型定义时，在 JDK7 及以上，使用 diamond 语法或全省略。
> **说明：**菱形泛型，即 diamond，直接使用<>来指代前边已经指定的类型。
> **正例：**
// diamond 方式，即<>HashMap<String, String> userCache = new HashMap<>(16); // 全省略方式ArrayList<User> users = new ArrayList(10);
16. [推荐] **【推荐】**集合初始化时，指定集合初始值大小。
> **说明：**HashMap 使用 HashMap(int initialCapacity) 初始化，如果暂时无法确定集合大小，那么指定默认值（16）即可。
> **正例：**initialCapacity = (需要存储的元素个数 / 负载因子) + 1。注意负载因子（即 loader factor）默认为 0.75，如果暂时无法确定初始值大小，请设置为 16（即默认值）。
> **反例：**HashMap 需要放置 1024 个元素，由于没有设置容量初始大小，随着元素不断增加，容量 7 次被迫扩大，resize 需要重建 hash 表。当放置的集合元素个数达千万级别时，不断扩容会严重影响性能。
17. [推荐] **【推荐】**使用 entrySet 遍历 Map 类集合 KV，而不是 keySet 方式进行遍历。
> **说明：**keySet 其实是遍历了 2 次，一次是转为 Iterator 对象，另一次是从 hashMap 中取出 key 所对应的value。而 entrySet 只是遍历了一次就把 key 和 value 都放到了 entry 中，效率更高。如果是 JDK8，使用 Map.forEach 方法。
> **正例：**values()返回的是 V 值集合，是一个 list 集合对象；keySet()返回的是 K 值集合，是一个 Set 集合对象；entrySet()返回的是 K-V 值组合集合。
18. [推荐] **【推荐】**高度注意 Map 类集合 K/V 能不能存储 null 值的情况，如下表格：
集合类	Key	Value	Super	说明Hashtable	不允许为null	不允许为null	Dictionary	线程安全ConcurrentHashMap	不允许为null	不允许为null	AbstractMap	锁分段技术(JDK8:CAS)TreeMap	不允许为null	允许为null	AbstractMap	线程不安全HashMap	允许为null	允许为null	AbstractMap	线程不安全
> **反例：**由于 HashMap 的干扰，很多人认为 ConcurrentHashMap 是可以置入 null 值，而事实上，存储 null 值时会抛出 NPE 异常。
19. [参考] **【参考】**合理利用好集合的有序性(sort)和稳定性(order)，避免集合的无序性(unsort)和不稳定性(unorder)带来的负面影响。
> **说明：**有序性是指遍历的结果是按某种比较规则依次排列的。稳定性指集合每次遍历的元素次序是一定的。
如：ArrayList 是 order/unsort；HashMap 是 unorder/unsort；TreeSet 是 order/sort。
20. [参考] **【参考】**利用 Set 元素唯一的特性，可以快速对一个集合进行去重操作，避免使用 List 的contains()进行遍历去重或者判断包含操作。

## 2.7 并发处理

1. [强制] **【强制】**获取单例对象需要保证线程安全，其中的方法也要保证线程安全。
> **说明：**资源驱动类、工具类、单例工厂类都需要注意。
2. [强制] **【强制】**创建线程或线程池时请指定有意义的线程名称，方便出错时回溯。
> **正例：**自定义线程工厂，并且根据外部特征进行分组，比如，来自同一机房的调用，把机房编号赋值给 whatFeaturOfGroup
public class UserThreadFactory implements ThreadFactory {private final String namePrefix;private final AtomicInteger nextId = new AtomicInteger(1);// 定义线程组名称，在 jstack 问题排查时，非常有帮助UserThreadFactory(String whatFeaturOfGroup) {
namePrefix = "From UserThreadFactory's " + whatFeaturOfGroup + "-Worker-";}@Overridepublic Thread newThread(Runnable task) {String name = namePrefix + nextId.getAndIncrement();Thread thread = new Thread(null, task, name, 0, false);
System.out.println(thread.getName());return thread;}}
3. [强制] **【强制】**线程资源必须通过线程池提供，不允许在应用中自行显式创建线程。
> **说明：**线程池的好处是减少在创建和销毁线程上所消耗的时间以及系统资源的开销，解决资源不足的问题。
如果不使用线程池，有可能造成系统创建大量同类线程而导致消耗完内存或者“过度切换”的问题。
4. [强制] **【强制】**线程池不允许使用Executors去创建，而是通过ThreadPoolExecutor的方式，这样的处理方式让写的同学更加明确线程池的运行规则，规避资源耗尽的风险。
> **说明：**Executors返回的线程池对象的弊端如下：
1） FixedThreadPool和SingleThreadPool：允许的请求队列长度为Integer.MAX_VALUE，可能会堆积大量的请求，从而导致 OOM。 2） CachedThreadPool：允许的创建线程数量为 Integer.MAX_VALUE，可能会创建大量的线程，从而导致 OOM。
5. [强制] **【强制】**SimpleDateFormat 是线程不安全的类，一般不要定义为 static 变量，如果定义为 static，必须加锁，或者使用 DateUtils 工具类。
> **正例：**注意线程安全，使用 DateUtils。亦推荐如下处理：
private static final ThreadLocal<DateFormat> df = new ThreadLocal<DateFormat>() { @Overrideprotected DateFormat initialValue() {return new SimpleDateFormat("yyyy-MM-dd");}};
> **说明：**如果是JDK8的应用，可以使用Instant代替Date，LocalDateTime代替Calendar， DateTimeFormatter代替SimpleDateFormat，官方给出的解释：simple beautiful strong immutable thread-safe。
6. [强制] **【强制】**必须回收自定义的 ThreadLocal 变量，尤其在线程池场景下，线程经常会被复用，如果不清理自定义的 ThreadLocal 变量，可能会影响后续业务逻辑和造成内存泄露等问题。
尽量在代理中使用 try-finally 块进行回收。
> **正例：**
objectThreadLocal.set(userInfo);try {// ...} finally {objectThreadLocal.remove();}
7. [强制] **【强制】**高并发时，同步调用应该去考量锁的性能损耗。能用无锁数据结构，就不要用锁；能锁区块，就不要锁整个方法体；能用对象锁，就不要用类锁。
> **说明：**尽可能使加锁的代码块工作量尽可能的小，避免在锁代码块中调用 RPC 方法。
8. [强制] **【强制】**对多个资源、数据库表、对象同时加锁时，需要保持一致的加锁顺序，否则可能会造成死锁。
> **说明：**线程一需要对表 A、B、C 依次全部加锁后才可以进行更新操作，那么线程二的加锁顺序也必须是 A、B、C，否则可能出现死锁。
9. [强制] **【强制】**在使用阻塞等待获取锁的方式中，必须在 try 代码块之外，并且在加锁方法与 try 代码块之间没有任何可能抛出异常的方法调用，避免加锁成功后，在 finally 中无法解锁。
说明一：如果在 lock 方法与 try 代码块之间的方法调用抛出异常，那么无法解锁，造成其它线程无法成功获取锁。 说明二：如果 lock 方法在 try 代码块之内，可能由于其它方法抛出异常，导致在 finally 代码块中，unlock对未加锁的对象解锁，它会调用 AQS 的 tryRelease 方法（取决于具体实现类），抛出 IllegalMonitorStateException 异常。
说明三：在 Lock 对象的 lock 方法实现中可能抛出 unchecked 异常，产生的后果与说明二相同。
> **正例：**
Lock lock = new XxxLock();// ...lock.lock();try {doSomething();doOthers();} finally {lock.unlock();}
> **反例：**
Lock lock = new XxxLock();// ...try {// 如果此处抛出异常，则直接执行 finally 代码块doSomething();// 无论加锁是否成功，finally 代码块都会执行lock.lock();doOthers();} finally {lock.unlock();}
10. [强制] **【强制】**在使用尝试机制来获取锁的方式中，进入业务代码块之前，必须先判断当前线程是否持有锁。锁的释放规则与锁的阻塞等待方式相同。
> **说明：**Lock对象的unlock方法在执行时，它会调用AQS的tryRelease方法（取决于具体实现类），如果当前线程不持有锁，则抛出IllegalMonitorStateException异常。
> **正例：**
Lock lock = new XxxLock();// ...boolean isLocked = lock.tryLock();if (isLocked) {try {doSomething();doOthers();} finally {lock.unlock();}}
11. [强制] **【强制】**并发修改同一记录时，避免更新丢失，需要加锁。要么在应用层加锁，要么在缓存加锁，要么在数据库层使用乐观锁，使用 version 作为更新依据。
> **说明：**如果每次访问冲突概率小于 20%，推荐使用乐观锁，否则使用悲观锁。乐观锁的重试次数不得小于3次。
12. [强制] **【强制】**多线程并行处理定时任务时，Timer运行多个TimeTask时，只要其中之一没有捕获抛出的异常，其它任务便会自动终止运行，使用ScheduledExecutorService则没有这个问题。
13. [推荐] **【推荐】**资金相关的金融敏感信息，使用悲观锁策略。
> **说明：**乐观锁在获得锁的同时已经完成了更新操作，校验逻辑容易出现漏洞，另外，乐观锁对冲突的解决策略有较复杂的要求，处理不当容易造成系统压力或数据异常，所以资金相关的金融敏感信息不建议使用乐观锁更新。
> **正例：**悲观锁遵循一锁二判三更新四释放的原则
14. [推荐] **【推荐】**使用CountDownLatch进行异步转同步操作，每个线程退出前必须调用countDown方法，线程执行代码注意catch异常，确保countDown方法被执行到，避免主线程无法执行至await方法，直到超时才返回结果。
> **说明：**注意，子线程抛出异常堆栈，不能在主线程 try-catch 到。
15. [推荐] **【推荐】**避免 Random 实例被多线程使用，虽然共享该实例是线程安全的，但会因竞争同一seed导致的性能下降。
> **说明：**Random 实例包括 java.util.Random 的实例或者 Math.random()的方式。
> **正例：**在 JDK7 之后，可以直接使用 API ThreadLocalRandom，而在 JDK7 之前，需要编码保证每个线程持有一个单独的 Random 实例。
16. [推荐] **【推荐】**通过双重检查锁（double-checked locking）（在并发场景下）实现延迟初始化的优化问题隐患(可参考 The "Double-Checked Locking is Broken" Declaration)，推荐解决方案中较为简单一种（适用于 JDK5 及以上版本），将目标属性声明为 volatile 型（比如修改 helper 的属性声明为`private volatile Helper helper = null;`）。
> **反例：**
public class LazyInitDemo {private Helper helper = null;public Helper getHelper() {if (helper == null) {synchronized (this) {if (helper == null) { helper = new Helper(); }}}return helper;}// other methods and fields...
}
17. [参考] **【参考】**volatile解决多线程内存不可见问题。对于一写多读，是可以解决变量同步问题，但是如果多写，同样无法解决线程安全问题。
> **说明：**如果是 count++操作，使用如下类实现：AtomicInteger count = new AtomicInteger(); count.addAndGet(1); 如果是 JDK8，推荐使用 LongAdder 对象，比 AtomicLong 性能更好（减少乐观锁的重试次数）。
18. [参考] **【参考】**HashMap 在容量不够进行 resize 时由于高并发可能出现死链，导致 CPU 飙升，在开发过程中注意规避此风险。
19. [参考] **【参考】**ThreadLocal 对象使用 static 修饰，ThreadLocal 无法解决共享对象的更新问题。
> **说明：**这个变量是针对一个线程内所有操作共享的，所以设置为静态变量，所有此类实例共享此静态变量，也就是说在类第一次被使用时装载，只分配一块存储空间，所有此类的对象(只要是这个线程内定义的)都可以操控这个变量。

## 2.8 控制语句

1. [强制] **【强制】**在一个 switch 块内，每个 case 要么通过 continue/break/return 等来终止，要么注释说明程序将继续执行到哪一个 case 为止；在一个 switch 块内，都必须包含一个 default语句并且放在最后，即使它什么代码也没有。
> **说明：**注意 break 是退出 switch 语句块，而 return 是退出方法体。
2. [强制] **【强制】**当 switch 括号内的变量类型为 String 并且此变量为外部参数时，必须先进行 null判断。
> **反例：**如下的代码输出是什么？
public class SwitchString {public static void main(String[] args) {method(null);}public static void method(String param) {switch (param) {// 肯定不是进入这里case "sth":System.out.println("it's sth");break;// 也不是进入这里
case "null":System.out.println("it's null");break;// 也不是进入这里default:System.out.println("default");}}}
3. [强制] **【强制】**在 if/else/for/while/do 语句中必须使用大括号。
> **说明：**即使只有一行代码，禁止不采用大括号的编码方式：if (condition) statements;
4. [强制] **【强制】**三目运算符 condition? 表达式 1 : 表达式 2 中，高度注意表达式 1 和 2 在类型对齐时，可能抛出因自动拆箱导致的 NPE 异常。
> **说明：**以下两种场景会触发类型对齐的拆箱操作：
表达式1或表达式2的值只要有一个是原始类型。 表达式1或表达式2的值的类型不一致，会强制拆箱升级成表示范围更大的那个类型。
> **反例：**
Integer a = 1;Integer b = 2;Integer c = null;Boolean flag = false;// a*b 的结果是 int 类型，那么 c 会强制拆箱成 int 类型，抛出 NPE 异常Integer result=(flag? a*b : c);
5. [强制] **【强制】**在高并发场景中，避免使用”等于”判断作为中断或退出的条件。
> **说明：**如果并发控制没有处理好，容易产生等值判断被“击穿”的情况，使用大于或小于的区间判断条件来代替。
> **反例：**判断剩余奖品数量等于 0 时，终止发放奖品，但因为并发处理错误导致奖品数量瞬间变成了负数，这样的话，活动无法终止。
6. [推荐] **【推荐】**当某个方法的代码行数超过 10 行时，return / throw 等中断逻辑的右大括号后加一个空行。
> **说明：**这样做逻辑清晰，有利于代码阅读时重点关注。
7. [推荐] **【推荐】**表达异常的分支时，少用 if-else 方式，这种方式可以改写成：
if (condition) {...return obj;}// 接着写 else 的业务逻辑代码;
> **说明：**如果非使用 if()...else if()...else...方式表达逻辑，避免后续代码维护困难，请勿超过3层。
> **正例：**超过 3 层的 if-else 的逻辑判断代码可以使用卫语句、策略模式、状态模式等来实现，其中卫语句示例如下：
public void findBoyfriend (Man man){if (man.isUgly()) {System.out.println("本姑娘是外貌协会的资深会员");return;}if (man.isPoor()) {System.out.println("贫贱夫妻百事哀");return;}if (man.isBadTemper()) {System.out.println("银河有多远，你就给我滚多远");
return;}System.out.println("可以先交往一段时间看看");}
8. [推荐] **【推荐】**除常用方法（如 getXxx/isXxx）等外，不要在条件判断中执行其它复杂的语句，将复杂逻辑判断的结果赋值给一个有意义的布尔变量名，以提高可读性。
> **说明：**很多 if 语句内的逻辑表达式相当复杂，与、或、取反混合运算，甚至各种方法纵深调用，理解成本非常高。如果赋值一个非常好理解的布尔变量名字，则是件令人爽心悦目的事情。
> **正例：**
// 伪代码如下final boolean existed = (file.open(fileName, "w") != null) && (...) || (...); if (existed) {...}
> **反例：**
public final void acquire ( long arg){if (!tryAcquire(arg) &&acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {selfInterrupt();}
9. [推荐] **【推荐】**不要在其它表达式（尤其是条件表达式）中，插入赋值语句。
> **说明：**赋值点类似于人体的穴位，对于代码的理解至关重要，所以赋值语句需要清晰地单独成为一行。
> **反例：**
public Lock getLock(boolean fair) {// 算术表达式中出现赋值操作，容易忽略 count 值已经被改变threshold = (count = Integer.MAX_VALUE) - 1;// 条件表达式中出现赋值操作，容易误认为是 sync==fairreturn (sync = fair) ? new FairSync() : new NonfairSync();
}
10. [推荐] **【推荐】**循环体中的语句要考量性能，以下操作尽量移至循环体外处理，如定义对象、变量、获取数据库连接，进行不必要的 try-catch 操作（这个 try-catch 是否可以移至循环体外）。
11. [推荐] **【推荐】**避免采用取反逻辑运算符。
> **说明：**取反逻辑不利于快速理解，并且取反逻辑写法必然存在对应的正向逻辑写法。
> **正例：**使用 if (x < 628) 来表达 x 小于 628。
> **反例：**使用 if (!(x >= 628)) 来表达 x 小于 628。
12. [推荐] **【推荐】**接口入参保护，这种场景常见的是用作批量操作的接口。
> **反例：**某业务系统，提供一个用户批量查询的接口，API 文档上有说最多查多少个，但接口实现上没做任何保护，导致调用方传了一个 1000 的用户 id 数组过来后，查询信息后，内存爆了。
13. [参考] **【参考】**下列情形，需要进行参数校验：
调用频次低的方法。 执行时间开销很大的方法。此情形中，参数校验时间几乎可以忽略不计，但如果因为参数错误导致中间执行回退，或者错误，那得不偿失。 需要极高稳定性和可用性的方法。 对外提供的开放接口，不管是 RPC/API/HTTP 接口。 敏感权限入口。
14. [参考] **【参考】**下列情形，不需要进行参数校验：
1)极有可能被循环调用的方法。但在方法说明里必须注明外部参数检查。 2)底层调用频度比较高的方法。毕竟是像纯净水过滤的最后一道，参数错误不太可能到底层才会暴露问题。一般 DAO 层与 Service 层都在同一个应用中，部署在同一台服务器中，所以 DAO 的参数校验，可以省略。 3)被声明成 private 只会被自己代码所调用的方法，如果能够确定调用方法的代码传入参数已经做过检查或者肯定不会有问题，此时可以不校验参数。

## 2.9 注释规约

1. [强制] **【强制】**类、类属性、类方法的注释必须使用 Javadoc 规范，使用/**内容*/格式，不得使用// xxx 方式。
> **说明：**在 IDE 编辑窗口中，Javadoc 方式会提示相关注释，生成 Javadoc 可以正确输出相应注释；在 IDE 中，工程调用方法时，不进入方法即可悬浮提示方法、参数、返回值的意义，提高阅读效率。
2. [强制] **【强制】**所有的抽象方法（包括接口中的方法）必须要用 Javadoc 注释、除了返回值、参数、异常说明外，还必须指出该方法做什么事情，实现什么功能。
> **说明：**对子类的实现要求，或者调用注意事项，请一并说明。
3. [强制] **【强制】**所有的类都必须添加创建者和创建日期。
> **说明：**在设置模板时，注意 IDEA 的@author 为`${USER}`，而 eclipse 的@author 为`${user}`，大小写有区别，而日期的设置统一为 yyyy/MM/dd 的格式。
> **正例：**
/***@author ${USER}*@date 2016/10/31*/
4. [强制] **【强制】**方法内部单行注释，在被注释语句上方另起一行，使用//注释。方法内部多行注释使用/* */注释，注意与代码对齐。
5. [强制] **【强制】**所有的枚举类型字段必须要有注释，说明每个数据项的用途。
6. [推荐] **【推荐】**与其“半吊子”英文来注释，不如用中文注释把问题说清楚。专有名词与关键字保持英文原文即可。
> **反例：**“TCP 连接超时”解释成“传输控制协议连接超时”，理解反而费脑筋。
7. [强制] **【强制】**代码修改的同时，注释也要进行相应的修改，尤其是参数、返回值、异常、核心逻辑等的修改。
> **说明：**代码与注释更新不同步，就像路网与导航软件更新不同步一样，如果导航软件严重滞后，就失去了导航的意义。
8. [推荐] **【推荐】**在类中删除未使用的任何字段和方法；在方法中删除未使用的任何参数声明与内部变量。
9. [参考] **【参考】**谨慎注释掉代码。在上方详细说明，而不是简单地注释掉。如果无用，则删除。
> **说明：**代码被注释掉有两种可能性：1）后续会恢复此段代码逻辑。2）永久不用。前者如果没有备注信息，难以知晓注释动机。后者建议直接删掉即可，假如需要查阅历史代码，登录代码仓库即可。
10. [参考] **【参考】**对于注释的要求：第一、能够准确反映设计思想和代码逻辑；第二、能够描述业务含义，使别的程序员能够迅速了解到代码背后的信息。完全没有注释的大段代码对于阅读者形同天书，注释是给自己看的，即使隔很长时间，也能清晰理解当时的思路；注释也是给继任者看的，使其能够快速接替自己的工作。
11. [参考] **【参考】**好的命名、代码结构是自解释的，注释力求精简准确、表达到位。避免出现注释的一个极端：过多过滥的注释，代码的逻辑一旦修改，修改注释是相当大的负担。
> **反例：**
// put elephant into fridgeput(elephant, fridge);方法名 put，加上两个有意义的变量名 elephant 和 fridge，已经说明了这是在干什么，语义清晰的代码不需要额外的注释。
12. [参考] **【参考】**特殊注释标记，请注明标记人与标记时间。注意及时处理这些标记，通过标记扫描，经常清理此类标记。线上故障有时候就是来源于这些标记处的代码。
1） 待办事宜（TODO）:（标记人，标记时间，[预计处理时间]）表示需要实现，但目前还未实现的功能。这实际上是一个 Javadoc 的标签，目前的 Javadoc 还没有实现，但已经被广泛使用。只能应用于类，接口和方法（因为它是一个 Javadoc 标签）。 2） 错误，不能工作（FIXME）:（标记人，标记时间，[预计处理时间]）在注释中用 FIXME 标记某代码是错误的，而且不能工作，需要及时纠正的情况。

## 2.10 异常处理

1. [强制] **【强制】**Java类库中定义的可以通过预检查方式规避的RuntimeException异常不应该通过catch的方式来处理，比如：NullPointerException，IndexOutOfBoundsException 等等。
> **说明：**无法通过预检查的异常除外，比如，在解析字符串形式的数字时，可能存在数字格式错误，不得不通过 catch NumberFormatException 来实现。
> **正例：**if (obj != null) {...}
> **反例：**try { obj.method(); } catch (NullPointerException e) {…}
2. [强制] **【强制】**异常不要用来做流程控制，条件控制。
> **说明：**异常设计的初衷是解决程序运行中的各种意外情况，且异常的处理效率比条件判断方式要低很多。
3. [强制] **【强制】**catch 时请分清稳定代码和非稳定代码，稳定代码指的是无论如何不会出错的代码。
对于非稳定代码的 catch 尽可能进行区分异常类型，再做对应的异常处理。
> **说明：**对大段代码进行 try-catch，使程序无法根据不同的异常做出正确的应激反应，也不利于定位问题，这是一种不负责任的表现。
> **正例：**用户注册的场景中，如果用户输入非法字符，或用户名称已存在，或用户输入密码过于简单，在程序上作出分门别类的判断，并提示给用户。
4. [强制] **【强制】**捕获异常是为了处理它，不要捕获了却什么都不处理而抛弃之，如果不想处理它，请将该异常抛给它的调用者。最外层的业务使用者，必须处理异常，将其转化为用户可以理解的内容。
5. [强制] **【强制】**事务场景中，抛出异常被 catch 后，如果需要回滚，一定要注意手动回滚事务。
6. [强制] **【强制】**finally 块必须对资源对象、流对象进行关闭，有异常也要做 try-catch。
> **说明：**如果 JDK7 及以上，可以使用 try-with-resources 方式。
7. [强制] **【强制】**不要在 finally 块中使用 return。
> **说明：**try 块中的 return 语句执行成功后，并不马上返回，而是继续执行 finally 块中的语句，如果此处存在 return 语句，则在此直接返回，无情丢弃掉 try 块中的返回点。
> **反例：**
private int x = 0;public int checkReturn() {try {// x 等于 1，此处不返回return ++x;} finally {// 返回的结果是 2return ++x;}}
8. [强制] **【强制】**捕获异常与抛异常，必须是完全匹配，或者捕获异常是抛异常的父类。
> **说明：**如果预期对方抛的是绣球，实际接到的是铅球，就会产生意外情况。
9. [推荐] **【推荐】**在调用 RPC、二方包、或动态生成类的相关方法时，捕捉异常必须使用 Throwable类来进行拦截。
> **说明：**通过反射机制来调用方法，如果找不到方法，抛出 NoSuchMethodException。什么情况会抛出 NoSuchMethodError 呢？二方包在类冲突时，仲裁机制可能导致引入非预期的版本使类的方法签名不匹配，或者在字节码修改框架（比如：ASM）动态创建或修改类时，修改了相应的方法签名。这些情况，即使代码编译期是正确的，但在代码运行期时，会抛出 NoSuchMethodError。
10. [推荐] **【推荐】**方法的返回值可以为 null，不强制返回空集合，或者空对象等，必须添加注释充分说明什么情况下会返回 null 值。
> **说明：**本手册明确防止 NPE 是调用者的责任。即使被调用方法返回空集合或者空对象，对调用者来说，也并非高枕无忧，必须考虑到远程调用失败、序列化失败、运行时异常等场景返回 null 的情况。
11. [推荐] **【推荐】**防止 NPE，是程序员的基本修养，注意 NPE 产生的场景：
返回类型为基本数据类型，return 包装数据类型的对象时，自动拆箱有可能产生 NPE。
> **反例：**public int f() { return Integer 对象}， 如果为 null，自动解箱抛 NPE。
数据库的查询结果可能为 null。集合里的元素即使 isNotEmpty，取出的数据元素也可能为 null。远程调用返回对象时，一律要求进行空指针判断，防止 NPE。对于 Session 中获取的数据，建议进行 NPE 检查，避免空指针。级联调用 obj.getA().getB().getC()；一连串调用，易产生 NPE。
> **正例：**使用 JDK8 的 Optional 类来防止 NPE 问题。
12. [推荐] **【推荐】**定义时区分unchecked/checked异常，避免直接抛出new RuntimeException()，更不允许抛出Exception或者Throwable，应使用有业务含义的自定义异常。推荐业界已定义过的自定义异常，如：DAOException/ServiceException等。
> **说明：**关于 RPC 方法返回方式使用 Result 方式的理由：
1)使用抛异常返回方式，调用方如果没有捕获到就会产生运行时错误。 2)如果不加栈信息，只是 new 自定义异常，加入自己的理解的 error message，对于调用端解决问题的帮助不会太多。如果加了栈信息，在频繁调用出错的情况下，数据序列化和传输的性能损耗也是问题。
14. [参考] **【参考】**避免出现重复的代码（Don't Repeat Yourself），即 DRY 原则。
> **说明：**随意复制和粘贴代码，必然会导致代码的重复，在以后需要修改时，需要修改所有的副本，容易遗漏。必要时抽取共性方法，或者抽象公共类，甚至是组件化。
> **正例：**一个类中有多个 public 方法，都需要进行数行相同的参数校验操作，这个时候请抽取： private boolean checkParam(DTO dto) {...}

## 2.11 日志规约

1. [推荐] **【推荐】**应用中不可直接使用日志系统（Log4j、Logback）中的 API，而应依赖使用日志框架（SLF4J、JCL--Jakarta Commons Logging）中的 API，使用门面模式的日志框架，有利于维护和各个类的日志处理方式统一。
> **说明：**日志框架（SLF4J、JCL--Jakarta Commons Logging）的使用方式（推荐使用 SLF4J）
使用 SLF4J：import org.slf4j.Logger;import org.slf4j.LoggerFactory;private static final Logger logger = LoggerFactory.getLogger(Test.class);使用 JCL：import org.apache.commons.logging.Log;import org.apache.commons.logging.LogFactory;
private static final Log log = LogFactory.getLog(Test.class);
2. [强制] **【强制】**所有日志文件至少保存 15 天，因为有些异常具备以“周”为频次发生的特点。对于当天日志，以“应用名.log”来保存，保存在/home/admin/应用名/logs/</font>目录下，过往日志格式为: {logname}.log.{保存日期}，日期格式：yyyy-MM-dd。
> **说明：**以mppserver应用为例，日志保存在/home/admin/mppserver/logs/mppserver.log，历史日志名称为 mppserver.log.2016-08-01
3. [强制] **【强制】**应用中的扩展日志（如打点、临时监控、访问日志等）命名方式：
appName_logType_logName.log。logType:日志类型，如 stats/monitor/access 等；logName:日志描述。这种命名的好处：通过文件名就可知道日志文件属于什么应用，什么类型，什么目的，也有利于归类查找。
> **说明：**推荐对日志进行分类，如将错误日志和业务日志分开存放，便于开发人员查看，也便于通过日志对系统进行及时监控。
> **正例：**mppserver 应用中单独监控时区转换异常，如：
mppserver_monitor_timeZoneConvert.log
4. [强制] **【强制】**在日志输出时，字符串变量之间的拼接使用占位符的方式。
> **说明：**因为 String 字符串的拼接会使用 StringBuilder 的 append()方式，有一定的性能损耗。使用占位符仅是替换动作，可以有效提升性能。
> **正例：**logger.debug("Processing trade with id: {} and symbol: {}", id, symbol);
5. [强制] **【强制】**对于 trace/debug/info 级别的日志输出，必须进行日志级别的开关判断。
> **说明：**虽然在 debug(参数)的方法体内第一行代码 isDisabled(Level.DEBUG_INT)为真时（Slf4j 的常见实现
Log4j 和 Logback），就直接 return，但是参数可能会进行字符串拼接运算。此外，如果 debug(getName()) 这种参数内有 getName()方法调用，无谓浪费方法调用的开销。
> **正例：**
// 如果判断为真，那么可以输出 trace 和 debug 级别的日志if (logger.isDebugEnabled()) {logger.debug("Current ID is: {} and name is: {}", id, getName());}
6. [强制] **【强制】**避免重复打印日志，浪费磁盘空间，务必在 log4j.xml 中设置 additivity=false。
> **正例：**<logger name="com.taobao.dubbo.config" additivity="false">
7. [强制] **【强制】**生产环境禁止直接使用 System.out 或 System.err 输出日志或使用e.printStackTrace()打印异常堆栈。
> **说明：**标准日志输出与标准错误输出文件每次 Jboss 重启时才滚动，如果大量输出送往这两个文件，容易造成文件大小超过操作系统大小限制。
8. [强制] **【强制】**异常信息应该包括两类信息：案发现场信息和异常堆栈信息。如果不处理，那么通过关键字 throws 往上抛出。
> **正例：**logger.error(各类参数或者对象 toString() + "_" + e.getMessage(), e);
9. [强制] **【强制】**日志打印时禁止直接用 JSON 工具将对象转换成 String。
> **说明：**如果对象里某些 get 方法被重写，存在抛出异常的情况，则可能会因为打印日志而影响正常业务流程的执行。
> **正例：**打印日志时仅打印出业务相关属性值或者调用其对象的 toString()方法。
10. [推荐] **【推荐】**谨慎地记录日志。生产环境禁止输出 debug 日志；有选择地输出 info 日志；如果使用 warn 来记录刚上线时的业务行为信息，一定要注意日志输出量的问题，避免把服务器磁盘撑爆，并记得及时删除这些观察日志。
> **说明：**大量地输出无效日志，不利于系统性能提升，也不利于快速定位错误点。记录日志时请思考：这些日志真的有人看吗？看到这条日志你能做什么？能不能给问题排查带来好处？
11. [推荐] **【推荐】**可以使用 warn 日志级别来记录用户输入参数错误的情况，避免用户投诉时，无所适从。如非必要，请不要在此场景打出 error 级别，避免频繁报警。
> **说明：**注意日志输出的级别，error 级别只记录系统逻辑出错、异常或者重要的错误信息。
12. [推荐] **【推荐】**尽量用英文来描述日志错误信息，如果日志中的错误信息用英文描述不清楚的话使用中文描述即可，否则容易产生歧义。
> **说明：**国际化团队或海外部署的服务器由于字符集问题，使用全英文来注释和描述日志错误信息。

# 3. 安全规范

## 3.1 安全编码基本原则

### 3.1 1.所有输入数据都是有害的

①　直接输入数据：对于用户通过 GET, POST, COOKIE, REQUEST等输入的数据以及框架提供的数据来源，即通信协议中从客户端传过来的一切变量，无论是用户手动填写的数据或是客户端浏览器或操作系统自动填写的数据，都可能产生安全问题，需要进行严格的安全性检查。 ②　间接的输入数据：从数据库、文件、网络、内部API获取的数据等，即一些不直接来源于用户，但是又不是程序中定义好的常量数据。比如用户的输入经过层层转化输出到数据库或文件，后面又再次利用的时候，这时获得的数据依然是不可信的，同样需要进行严格的安全性检查。

### 3.1 2.不依赖运行环境的安全配置

不能寄希望于配置文件的安全选项，必须将程序置身于最不安全的配置下进行考虑。

### 3.1 3.安全控制措施落实在最后执行阶段

每个安全问题都有其产生的原因,例如SQL注入的原因是SQL语句参数拼接。因此对SQL注入问题的防范，需要在SQL语句执行前对参数进行安全处理，因为此时才能确定预期的参数数据类型、数据范围等。

### 3.1 4.最小化

最小化原则适用于所有安全相关的领域，在代码安全方面主要表现为：①　用户输入最小化。尽可能少地使用用户的输入。 ②　用户输入范围最小化。过滤参数时应使用白名单策略，对于可明确定义范围的参数检查参数的有效性，譬如Email，卡号，身份证号等。 ③　返回信息最小化。程序错误信息等应对用户屏蔽，不要将原始错误信息直接返回到用户侧。

### 3.1 5.失败终止

对用户提交的数据进行安全性检查的时候，如果发现数据不符合要求应终止业务的执行，不要试图修正和转换用户提交的参数继续向下执行。

## 3.2 Web应用开发

### 3.2 1.避免注入

> **说明：**注入往往是程序缺少对输入进行安全性检查所引起的，攻击者把一些包含指令的数据发送给解释器，解释器会把收到的指令转换成指令执行。
漏洞原因：未审计的数据输入框、使用网址直接传递变量、未过滤的特殊字符、SQL 错误回显。 危害：注入可能导致数据丢失或数据破坏、缺乏可审计性 或是拒绝服务。注入漏洞有时甚至能导致完全接管主机。 注入有很多类型，常见的注入包括：SQL、OS 命令、ORM、LDAP和表达式语言或者 OGNL 注入，对于应用解释器来说这些概念都是相同的。
1. [强制] **【强制】**避免SQL注入。
> **说明：**SQL注入是一类危害极大的攻击形式。虽然危害很大，但是防御却远远没有XSS那么困难。SQL注入漏洞存在的原因，就是拼接SQL参数。对于最常见的SQL注入，后端开发人员经常会拼接SQL查询；在不经意间就引入了SQL注入漏洞。
SQL攻击的总体思路：寻找到SQL注入的位置判断服务器类型和后台数据库类型针对不通的服务器和数据库特点进行SQL注入攻击
> **反例：**
select * from users where pwd='输入字符'-- 恶意代绕过 ' or 1=1 --'select * from uses where pwd = '' or 1=1 --'防范要求：1)【强制】SQL操作使用PreParedStatement
> **说明：**使用PreparedStatement预编译SQL,解决SQL注入问题，传递给PreparedStatement对象的参数可以被强制进行类型转换，确保在插入或查询数据时与底层的数据库格式匹配。
sql注入只对sql语句的准备(编译)过程有破坏作用，而PreparedStatement已经准备好了,执行阶段只是把输入串作为数据处理,而不再对sql语句进行解析,准备,因此也就避免了sql注入问题。
> **正例：**
HttpServletRequest request = ...;String userName = request.getParameter("name");if(null==userName){//handle error}Connection con = ...String query = "SELECT * FROM Users where user=?";PreparedStatement pre=conn.prepareStatement(query);
pre.setString(1, userName);pre.execute();2)【强制】白名单过滤
> **说明：**对于表名、列名等无法进行预编译的场景，比如外部数据拼接到order by, group by语句中，需通过白名单的形式对数据进行校验，例如判断传入列名是否存在、升降序仅允许输入“ASC”和“DESC”、表明列名仅允许输入字符、数字、下划线等。
> **正例：**
public String someMethod(boolean sortOrder) {String SQLquery = "some SQL ... order by Salary " + (sortOrder ? "ASC" : "DESC");`3)【推荐】使用正则表达式过滤传入的参数
> **说明：**采用正则表达式将包含有 单引号(')，分号(;) 和 注释符号(--)的语句给替换掉来防止SQL注入。还要小心 （OR）等敏感词汇。sql.replaceAll(".*([';]+|(--)+).*", " ");
4)【强制】Mybatis 使用#{ }，
> **说明：**相当于使用PreparedStatement
> **正例：**
<select id="getByPage" resultType="com.domain.Users" parameterType="com.Param">SELECTusername,idFROM tb_usersWHERE isdeleted=1<if test="name!=null and name!=''">AND nickname LIKE CONCAT('%', #{name}, '%')
</if>ORDER BYcreatetime DESClimit #{fromIndex},#{count}</select>5)【推荐】添加过滤器
> **说明：**如果使用Druid，连接池已经实现了此过滤器wallfilter触发后默认返回SQLException，只需要配置即可。
Spring MVC: https://github.com/alibaba/druid/wiki/%E9%85%8D%E7%BD%AE-wallfilterSpring BOOT: https://github.com/alibaba/druid/tree/master/druid-spring-boot-starter6)【强制】避免XML注入
> **说明：**通过StringBulider 或 StringBuffer 拼接XML文件时，需对输入数据进行合法性校验。 对数量quantity 进行合法性校验，控制只能传入0-9的数字：
if (!Pattern.matches("[0-9]+", quantity)) {// Format violation}String xmlString = "<item>\n<description>Widget</description>\n" +"<price>500</price>\n" +"<quantity>" + quantity + "</quantity></item>";
outStream.write(xmlString.getBytes());outStream.flush();

### 3.2 2.身份认证

> **说明：**与认证和会话管理相关的应用程序往往得不到正确的实施，这就导致攻击者破坏密码、秘钥、会话令牌、或利用实施漏洞冒充其他用户身份漏洞原因：未审计的数据输入框、使用网址直接传递变量、未过滤的特殊字符、SQL 错误回显。
危害： 这种漏洞可能导致部分甚至全部账户遭受攻击。一旦攻击成功，攻击者可以执行合法用户的全部操作。因此特权账户造成更大的破坏。
> **反例：**
1.Web应用程序支持URL重写，把会话ID写在URL里，修改订单的会话ID可用其他人的会话和信用卡付账。 2.应用程序超时设置不当，当使用公共计算机后为注销账户直接关闭浏览器后。攻击者能使用相同的浏览器通过身份认证。 3.内部或者外部攻击者进入数据库后，密码没有进行加密，所有用户密码都会被攻击者获得。 防范要求：1）【强制】只使用 HTTP Post 请求传输身份验证的凭据信息。 2）【强制】缺省不允许使用弱口令。
3）【强制】使用复杂密码（字符、数字、特殊字符，至少8位）。 4）【强制】输入的密码应当在用户的屏幕上模糊显示（***，不许显示明文）。 5）【强制】当连续多次登录失败后（默认5 次），应强制锁定账户。 6）【强制】普通用户不具备重置密码功能。 7）【强制】密码应定期修改。 8）【强制】单点登录的token绝对不允许使用登录名，不允许使用永久有效的token（默认设置30分钟过期），同一用户每次生成的token必须不一样。

### 3.2 3.敏感信息泄漏

> **说明：**许多Web应用程序和API都无法正确保护敏感数据，例如：财务数据、医疗数据和PII数据。攻击者可以通过窃取或修改未加密的数据来实施信用卡诈骗、身份盗窃或其他犯罪行为。未加密的敏感数据容易受到破坏，因此，我们需要对敏感数据加密，这些数据包括：传输过程中的数据、存储的数据 以及浏览器的交互数据。
防范要求：1）【强制】不要在错误响应中泄露敏感信息，包括：系统的详细信息、会话标识符或者帐号信息。例如：不要响应“用户名错误” 或 “密码错误”而应该响应为“用户名或密码错误”。 2）【强制】不要在日志中保存敏感信息，包括：不必要的系统详细信息、会话标识符或密码，例如：在日志中记录了token信息或密码信息3）【强制】不允许暴露异常的敏感信息，没有过滤敏感信息的异常堆栈往往会导致信息泄漏。
> **反例：**
try {FileInputStream fis =new FileInputStream(System.getenv("APPDATA") + args[0]);} catch (FileNotFoundException e) {// Log the exceptionthrow new IOException("Unable to retrieve file", e);}
> **正例：**
class ExceptionExample {public static void main(String[] args) {File file = null;try {file = new File(System.getenv("APPDATA") +args[0]).getCanonicalFile();if (!file.getPath().startsWith("c:\\homepath")) {
log.error("Invalid file");return;}} catch (IOException x) {log.error("Invalid file");return;}try {FileInputStream fis = new FileInputStream(file);} catch (FileNotFoundException x) {log.error("Invalid file");
return;}}}4）【强制】在满足业务需求的情况下，个人敏感信息需脱敏展示例如：鉴权信息（如口令、密保答案等）不允许展示身份证只显示第一位和最后一位字符，如3****1。 移动电话号码隐藏中间6位字符，如134**48。 工作地址/家庭地址最多显示到“区”一级。 银行卡号仅显示最后4位字符，如****86395）【强制】敏感类不允许复制包含私人的，机密或其他敏感数据的类是不允许被复制的，解决的方法有两种：
1、类声明为finalfinal class SensitiveClass {// ...}2、Clone 方法抛出CloneNotSupportedException异常class SensitiveClass {// ...public final SensitiveClass clone() throws CloneNotSupportedException {throw new CloneNotSupportedException();
}}6）【强制】不要硬编码敏感信息硬编码的敏感信息，如密码，服务器IP地址和加密密钥，可能会泄露给攻击者。敏感信息均必须存在在配置文件或数据库中。

### 3.2 4.访问控制

> **说明：**未对通过身份验证的用户实施恰当的访问控制。攻击者可以利用这些缺陷访问未经授权的功能或数 据，例如：访问其他用户的帐户、查看敏感文件、修改其他用户的数据、更改访问权限等。
危害：攻击者能够很容易的把网址改成享有特权的页面，这样就可以使用匿名或者普通用户访问私人界面，从而提升未授权功能和相关数据信息。
> **反例：**
1.通过验证的非管理员用户可以访问管理界面。 2.绕过路径，如未读取的参数做检查，导致路径绕过读取到敏感文件防范要求：1）【强制】查询个人非公开信息时，需要对当前访问账号进行数据权限校验。 包括：①　验证当前用户的登录状态；②　校验的当前请求账号的身份信息（如：token）；③　禁止从用户请求参数或Cookie中获取外部传入不可信用户身份直接进行查询；④　校验当前用户是否具备访问数据的权限。 2）【强制】各服务间的访问，都需要明确的授权验证。
3）【强制】限制上传下载文件的类型。
> **说明：**对文件的管理要确保：
校验上传文件大小文件类型是否符合要求不可直接使用参数中的原文件名，要随机生成文件名，并限定后缀保存到文件服务器中
> **正例：**
private Long FILE_MAX_SIZE = 100L*1024*1024;//100M@RequestMapping(value = "/upload", method = POST)@ResponseBodypublic String upload(@RequestParam("file") MultipartFile file) {if(null == file){//handle error
}Long filesize = file.getSize();if(FILE_MAX_SIZE<filesize){//handle errorreturn "error";}String file_name = file.getOriginalFilename();String[] parts = file_name.split("\\.");String suffix = parts[parts.length - 1];
switch (suffix){case "jpeg":suffix = ".jpeg";break;case "jpg":suffix = ".jpg";break;case "bmp":suffix = ".bmp";break;case "png":suffix = ".png";break;default://handle errorreturn "error";}if(!file.isEmpty()) {
long now = System.currentTimeMillis();File tempFile = new File(now + suffix);FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);//将tempFile保存到文件服务器中，然后删除tempFile}return "OK";}4）【强制】隶属于用户个人的页面或者功能必须进行权限控制校验。

### 3.2 5.安全配置

> **说明：**安全配置错误通常是由于不安全的默认配置、不完整的临时配置、开源云存储、错误的HTTP标头配置以及包含敏感信息的详细错误信息所造成的。它可以发生在一个应用程堆栈的任何层面，包括平台、Web服务器、应用服务器、数据库、架构和自定义代码。攻击者通过访问默认账户、未使用的网页、未安装补丁的漏洞、未被保护的文件和目录等，以获得对系统未授权的访问。
危害：系统可能在未知情况下被完全攻破，用户数据可能随着时间推移而全部被盗或者篡改。甚至有时，会导致整个系统被破坏。例如：1.Web服务器或者Web应用后台默认密码未更改，攻击者可以通过默认密码进入系统。 2.目录列表在Web服务器上未被禁用，攻击者可以列出目录获取目录结构甚至自定义代码。 防范要求：1）【强制】定期检查所使用应用框架的最新版本，及时更新或安装补丁。 2）【强制】数据库、服务器、框架不允许使用默认用户名和简单密码。
不允许使用root用户，密码必须包含大小写字母和符号。
> **反例：**
①　数据库使用root用户。 ②　数据库用户密码设置为123456。 3）【推荐】对所有上传的文件重命名，不使用简单命名。 4）【强制】定期检查并移除临时文件和备份文件。 5）【强制】控制上传文件的权限，关闭执行权限。

### 3.2 6.跨站

### 3.2 6.1.跨站脚本攻击Cross-Site Scripting (XSS)

> **说明：**当应用程序发送给浏览器的页面中包含用户提交的数据，但没有经过适当验证或转义时，就会导致跨站脚本漏洞。
危害：攻击者能够在受害者浏览器中执行脚本，以劫持用户会话(窃取Cookie)、迫害网站、插入恶意内容(组建僵尸网络)、重定向用户、使用恶意软件劫持用户浏览器等。简单来讲：就是可执行任意JavaScript能实现的功能。 防范要求：应编码后输出，输出到前端不同的html标签或属性中时，就采用不同的编码方法。 1）【推荐】web项目通过Filter +HttpServletRequestWrapper过滤+jsou过滤，对产生跨站的参数进行严格过滤，禁止传入<SCRIPT>标签。
> **正例：**
//定义需过滤的字段串<script>String s = "\uFE64" + "script" + "\uFE65";// 过滤字符串标准化s = Normalizer.normalize(s, Form.NFKC);// 使用正则表达式匹配inputStr是否存在<script>Pattern pattern = Pattern.compile(inputStr);Matcher matcher = pattern.matcher(s);
if (matcher.find()) {// Found black listed tagthrow new IllegalStateException();} else {// ...}2）【推荐】使用Spring框架时，可用框架自带的HtmlUtils.htmlEscape编码输出到html实体。
> **正例：**
@RequestMapping("/xsstest")public String xssTest(@RequestParam("id") String id, Model model){id=HtmlUtils.htmlEscape(id);model.addAttribute("id",id);return "index";}3）【推荐】使用Spring框架时，可用框架自带的HtmlUtils.htmlEscape编码输出到html实体。
> **正例：**
@RequestMapping("/xsstest")public String xssTest(@RequestParam("id") String id, Model model){id=HtmlUtils.htmlEscape(id);model.addAttribute("id",id);return "index";}4）【推荐】使用ESAPI时。 //参数输出到html实体， <div>..xssinput..</div>
String safe = ESAPI.encoder().encodeForHTML(xssInput);//参数输出到html标签的属性， <div attr=.. xssinput..>content</div>String safe = ESAPI.encoder().encodeForHTMLAttribute(xssInput);//参数输出到JavaScript中， <script>x='...xssInput...'</script>
String safe = ESAPI.encoder().encodeForJavaScript(xssInput);//富文本ESAPI.validator(). getValidSafeHTML()5）【推荐】未使用框架时，可使commons-lang库的StringEscapeUtils.escapeHtml编码输出到html实体。
> **正例：**
<%String id=request.getParameter("id");out.println(StringEscapeUtils.escapeHtml(id));%>

### 3.2 6.2.跨站请求伪造Cross-Site Request Forgery (CSRF)

> **说明：**由于浏览器自动发送会话Cookie等认证凭证，导致攻击者能够创建恶意的Web页面来产生伪造请求，这些伪造请求很难和合法的请求区分开。听起来像跨站脚本（XSS），但它与XSS非常不同，XSS利用站点内的信任用户，而CSRF则通过伪装来自受信任用户的请求来利用受信任的网站。
危害：攻击者能够让受害用户修改任何允许修改的数据，并执行任何用户允许的操作。修改密码、登录注销等。
> **反例：**
123456：李白的用户ID741741：杜甫的用户ID941941：白居易的用户ID李白正在某银行网站给白居易转账，则有以下URL：http://www.abc.com/zhuanzhang?=amount=500&fromAccount=123456&toAccount=941941杜甫构造一个请求，把李白账户中的钱转到自己账户中(并且修改了金额为5000)<img http://www.abc.com/zhuanzhang?=amount=5000&fromAccount=123456&
toAccount=741741" width="0" height="0">杜甫在他控制的多个网站中嵌入这段代码，李白只要登录了银行网站，又恰巧访问了杜甫控制的网站，李白的5000块钱就会转给杜甫。 编码正例：web passport认证通过后会在cookie植入csrf_token。此类安全问题前端应从cookie中获取csrf_token，以POST方式提交包含csrf_token值的请求，
代码如下：function getCookie() {var value = "; " + document.cookie;var parts = value.split("; csrf_token=");if (parts.length == 2)return parts.pop().split(";").shift();}$.ajax({type: "post",url: "/xxxx",data: {csrf_token:getCookie()},
dataType: "json",success: function (data) {if (data.ec == 200) {//do something}}});后端应从POST请求体中提取csrf_token参数值，进行校验，代码如下：public boolean isCSRFProtectPassed(String session,String csrf_token){if (null==session || null==csrf_token){
return false;}if (session.length()!=32 || csrf_token.length()!=32){return false;}if (csrf_token.equals(getCSRFTokenBySession(session))){return true;}return false;}其中getCSRFTokenBySession方法实现如下：public String getCSRFTokenBySession(String session){
return md5(session);}防范要求：
1. [强制] **【强制】**设置CSRF Token
服务端给合法的客户颁发CSRF Token，客户端在发送请求时携带该token供服务端校验，服务端拒绝token验证不通过的请求。以此来防止第三方构造合法的恶意操作链接。Token的作用域可以是Request级或者Session级。下面以Session级CSRF Token进行示例登录成功后颁发Token，并同时存储在服务端Session中String uuidToken = UUID.randomUUID().toString();
map.put("token", uuidToken);request.getSession().setAttribute("token",uuidToken );return map;创建Filterpublic class CsrfFilter implements Filter {...HttpSession session = req.getSession();Object token = session.getAttribute("token");
String requestToken = req.getParameter("token");if(StringUtils.isBlank(requestToken) || !requestToken.equals(token)){AjaxResponseWriter.write(req, resp, ServiceStatusEnum.ILLEGAL_TOKEN, "非法的token");return;
}...​CSRF Token应具备随机性，保证其不可预测和枚举。另外由于浏览器会自动对表单所访问的域名添加相应的cookie信息，所以CSRF Token不应该通过Cookie传输。
2. [强制] **【强制】**校验Referer头（与1选其一）
通过检查HTTP请求的Referer字段是否属于本站域名，非本站域名的请求进行拒绝。 这种校验方式需要注意两点：需要处理Referer为空的情况，当Referer为空则拒绝请求注意避免例如域名部分匹配的情况。

### 3.2 7.组件漏洞

> **说明：**应用程序使用带有已知漏洞的组件会破坏应用程序防御系统。
危害：可能导致严重的数据丢失或服务器接管。 防范要求：1)【强制】定期识别正在用的组件版本，及时获取新版本和安装补丁修复组件漏洞。 2)【强制】定期检查并移除不再使用的依赖组件。

### 3.2 8.不安全的直接对象引用

> **说明：**向一个已经授权的用户，通过更改访问时的一个参数，从而访问到原本其并没有得到授权的对象。
危害：这种漏洞能够损害参数所引用的所有数据。除非名字空间很稀疏，否则攻击者很容易访问该类型的所有数据。
> **反例：**1.攻击者发现他自己的参数是6065，即？id=6065；他可以直接更改参数为6066，即？id=6066；这样他就可以直接看到6066的账户信息。
防范要求：1)【强制】不允许在URl或网页中直接引用内部文件名或数据库关键字。 2)【强制】验证用户输入的URL请求，拒绝包含./或../的请求。 3)【推荐】使用基于用户或会话的间接对象访问，这样可防止攻击者直接攻击为授权资源。 4)【强制】对任何来自不受信源所使用的所有对象进行访问控制检查。

## 3.3 数据安全

1. [强制] **【强制】**避免类初始化的相互依赖。
> **说明：**错误的写法：
public class Cycle {private final int balance;private static final Cycle c = new Cycle();private static final int deposit = (int) (Math.random() * 100); // Random depositpublic Cycle() {balance = deposit - 10; // Subtract processing fee
}public static void main(String[] args) {System.out.println("The account balance is: " + c.balance);}}类加载时初始化指向Cycle类的静态变量c，而类Cycle的无参构造方法又依赖静态变量deposit，导致无法预期的结果。 正确的写法：public class Cycle {private final int balance;
private static final int deposit = (int) (Math.random() * 100); // Random depositprivate static final Cycle c = new Cycle();  // Inserted after initialization of required fieldspublic Cycle() {balance = deposit - 10; // Subtract processing fee
}public static void main(String[] args) {System.out.println("The account balance is: " + c.balance);}}
2. [推荐] **【推荐】**不可忽略方法的返回值。
> **说明：**忽略方法的返回值可能会导致无法预料的结果。
错误的写法：public void deleteFile(){File someFile = new File("someFileName.txt");someFile.delete();}正确的写法：public void deleteFile(){File someFile = new File("someFileName.txt");if (!someFile.delete()) {// handle failure to delete the file
}}
3. [强制] **【强制】**不要引用空指针。
> **说明：**当一个变量指向一个NULL值，使用这个变量的时候又没有检查，这时会导致。NullPointerException。在使用变量前一定要做是否为NULL值的校验。
Object obj = getObject();if (obj != null){obj.toString();}
4. [强制] **【强制】**使用Arrays.equals()来比较数组的内容。
> **说明：**数组没有覆盖的Object. equals()方法，调用Object. equals()方法实际上是比较数组的引用，而不是他们的内容。程序必须使用两个参数Arrays.equals()方法来比较两个数组的内容
public void arrayEqualsExample() {int[] arr1 = new int[20]; // initialized to 0int[] arr2 = new int[20]; // initialized to 0Arrays.equals(arr1, arr2); // true}
5. [强制] **【强制】**防止整数溢出。
> **说明：**使用java.lang.Number. BigInteger类进行整数运算，防止整数溢出。
基本操作 add，subtract，multiply，divide，compareTopublic class BigIntegerUtil {private static final BigInteger bigMaxInt = BigInteger.valueOf(Integer.MAX_VALUE);private static final BigInteger bigMinInt = BigInteger.valueOf(
Integer.MIN_VALUE);public static BigInteger intRangeCheck(BigInteger val)throws ArithmeticException {if (val.compareTo(bigMaxInt) == 1 || val.compareTo(bigMinInt) == -1) {throw new ArithmeticException("Integer overflow");
}return val;}public static int addInt(int v1, int v2) throws ArithmeticException {BigInteger b1 = BigInteger.valueOf(v1);BigInteger b2 = BigInteger.valueOf(v2);BigInteger res = intRangeCheck(b1.add(b2));
return res.intValue();}public static int subInt(int v1, int v2) throws ArithmeticException {BigInteger b1 = BigInteger.valueOf(v1);BigInteger b2 = BigInteger.valueOf(v2);BigInteger res = intRangeCheck(b1.subtract(b2));
return res.intValue();}public static int multiplyInt(int v1, int v2) throws ArithmeticException {BigInteger b1 = BigInteger.valueOf(v1);BigInteger b2 = BigInteger.valueOf(v2);BigInteger res = intRangeCheck(b1.multiply(b2));
return res.intValue();}public static int divideInt(int v1, int v2) throws ArithmeticException {BigInteger b1 = BigInteger.valueOf(v1);BigInteger b2 = BigInteger.valueOf(v2);BigInteger res = intRangeCheck(b1.divide(b2));
return res.intValue();}}
6. [强制] **【强制】**避免除法和取模运算分母为零。
> **说明：**要避免因为分母为零而导致除法和取模运算出现异常。
if (num2 == 0) {// handle error} else {result1= num1 /num2;result2= num1 % num2;}
7. [强制] **【强制】**数据成员声明为私有，提供可访问的包装方法。
> **说明：**攻击者可以用意想不到的方式操纵public或protected的数据成员，所以需要将数据成员为private，对外提供可控的包装方法访问数据成员。
8. [强制] **【强制】**敏感类不允许复制。
> **说明：**包含私人的，机密或其他敏感数据的类是不允许被复制的，解决的方法有两种：
1、类声明为finalfinal class SensitiveClass {// ...}2、Clone 方法抛出CloneNotSupportedException异常class SensitiveClass {// ...public final SensitiveClass clone() throws CloneNotSupportedException {throw new CloneNotSupportedException();
}}
9. [推荐] **【推荐】**类比较使用正确的方法。
> **说明：**如果由同一个类装载器装载，它们具有相同的完全限定名称，则它们是两个相同的类。
错误的写法：// Determine whether object auth has required/expected class objectif (auth.getClass().getName().equals("com.application.auth.DefaultAuthenticationHandler")) {// ...}正确写法：// Determine whether object auth has required/expected class name
if (auth.getClass() == com.application.auth.DefaultAuthenticationHandler.class) {// ...}
10. [参考] **【参考】**不要使用过时、陈旧或低效的方法。
> **说明：**在程序代码中使用过时的、陈旧的或低效的类或方法可能会导致错误的行为。
11. [推荐] **【推荐】**数组引用使用正确的方法。
> **说明：**某个方法返回一个对敏感对象的内部数组的引用，假定该方法的调用程序不改变这些对象。即使数组对象本身是不可改变的，也可以在数组对象以外操作数组的内容，这种操作将反映在返回该数组的对象中。如果该方法返回可改变的对象，外部实体可以改变在那个类中声明的 public 变量，这种改变将反映在实际对象中。
错误的写法：public class XXX {private String[] xxxx;public String[] getXXX() {return xxxx;}}正确的写法：public class XXX {private String[] xxxx;public String[] getXXX()return  Arrays.copyof(…);  // 或其他数组复制方法}}
12. [强制] **【强制】**不要忽略捕获的异常。
> **说明：**对于捕获的异常要进行相应的处理，不能忽略已捕获的异常
错误的写法：class Foo implements Runnable {public void run() {try {Thread.sleep(1000);} catch (InterruptedException e) {// 此处InterruptedException被忽略}}}正确的写法：class Foo implements Runnable {public void run() {
try {Thread.sleep(1000);} catch (InterruptedException e) {Thread.currentThread().interrupt(); // Reset interrupted status}}}
13. [强制] **【强制】**不允许抛出RuntimeException, Exception,Throwable。
> **说明：**错误的写法：
boolean isCapitalized(String s) {if (s == null) {throw new RuntimeException("Null String");}}private void doSomething() throws Exception {//...}正确的写法：boolean isCapitalized(String s) {if (s == null) {throw new NullPointerException();
}}private void doSomething() throws IOException {//...}
14. [强制] **【强制】**不要捕获NullPointerException或其他父类异常。
> **说明：**错误的写法：
boolean isName(String s) {try {String names[] = s.split(" ");if (names.length != 2) {return false;}return (isCapitalized(names[0]) && isCapitalized(names[1]));} catch (NullPointerException e) {return false;
}}正确的写法：boolean isName(String s) /* throws NullPointerException */ {String names[] = s.split(" ");if (names.length != 2) {return false;}return (isCapitalized(names[0]) && isCapitalized(names[1]));}
15. [强制] **【强制】**确保共享变量的可见性。
> **说明：**对于共享变量，要确保一个线程对它的改动对其他线程是可见的。线程可能会看到一个陈旧的共享变量的值。为了共享变量是最新的，可以将变量声明为volatile或lock同步读取和写入操作。
将共享变量声明为volatile：final class ControlledStop implements Runnable {private volatile boolean done = false;@Override public void run() {while (!done) {try {// ...Thread.currentThread().sleep(1000); // Do something
} catch(InterruptedException ie) {Thread.currentThread().interrupt(); // Reset interrupted status}}}public void shutdown() {done = true;}}同步读取和写入操作：final class ControlledStop implements Runnable {private boolean done = false;
@Override public void run() {while (!isDone()) {try {// ...Thread.currentThread().sleep(1000); // Do something} catch(InterruptedException ie) {Thread.currentThread().interrupt(); // Reset interrupted status
}}}public synchronized boolean isDone() {return done;}public synchronized void shutdown() {done = true;}}
16. [强制] **【强制】**共享变量的操作必须是原则性的。
> **说明：**除了要确保共享变量的更新对其他线程可见的，还需要确保对共享变量的操作是原子的，这时将共享变量声明为volatile往往是不够的。需要使用同步机制或Lock 同步读取和写入操作：
final class Flag {private volatile boolean flag = true;public synchronized void toggle() {flag ^= true; // Same as flag = !flag;}public boolean getFlag() {return flag;}}使用读取锁确保读取和写入操作的原子性final class Flag {
private boolean flag = true;private final ReadWriteLock lock = new ReentrantReadWriteLock();private final Lock readLock = lock.readLock();private final Lock writeLock = lock.writeLock();public void toggle() {
writeLock.lock();try {flag ^= true; // Same as flag = !flag;} finally {writeLock.unlock();}}public boolean getFlag() {readLock.lock();try {return flag;} finally {readLock.unlock();}}}
17. [强制] **【强制】**不要调用Thread.run()，不要使用Thread.stop()以终止线程。
18. [强制] **【强制】**确保执行阻塞操作的线程可以终止。
> **说明：**
public final class SocketReader implements Runnable {private final SocketChannel sc;private final Object lock = new Object();public SocketReader(String host, int port) throws IOException {sc = SocketChannel.open(new InetSocketAddress(host, port));
}@Override public void run() {ByteBuffer buf = ByteBuffer.allocate(1024);try {synchronized (lock) {while (!Thread.interrupted()) {sc.read(buf);// ...}}} catch (IOException ie) {// Forward to handler}}
public static void main(String[] args)throws IOException, InterruptedException {SocketReader reader = new SocketReader("somehost", 25);Thread thread = new Thread(reader);thread.start();Thread.sleep(1000);
thread.interrupt();}}
19. [强制] **【强制】**相互依存的任务不要在一个有限的线程池执行。
> **说明：**有限线程池指定可以同时执行在线程池中的线程数量的上限。程序不得使用有限线程池线程执行相互依赖的任务。可能会导致线程饥饿死锁，所有的线程池执行的任务正在等待一个可用的线程中执行一个内部队列阻塞，或是相关任务被拒绝的风险
20. [强制] **【强制】**锁放在事务之外。
> **说明：**当锁放在事务之内时，会造成多个线程同时启动事务，然后在等待锁，由于事物的隔离性，可能会造成大范围幻读。
21. [强制] **【强制】**不要序列化未加密的敏感数据。
> **说明：**序列化允许一个对象的状态被保存为一个字节序列，然后重新在稍后的时间恢复，它没有提供任何机制来保护序列化的数据。敏感的数据不应该被序列化。解决方法包括加密密钥，数字证书：对于数据成员可以使用transient，声明该数据成员是瞬态的。
重写序列化相关方法writeObject、readObject、readObjectNoData，防止被子类恶意重写。 class SensitiveClass extends Number {// ...protected final Object writeObject(java.io.ObjectOutputStream out)throws NotSerializableException {
throw new NotSerializableException();}protected final Object readObject(java.io.ObjectInputStream in)throws NotSerializableException {throw new NotSerializableException();}protected final Object readObjectNoData(java.io.ObjectInputStream in)
throws NotSerializableException {throw new NotSerializableException();}}

## 3.4 通讯安全

1. [强制] **【强制】**响应包的HTTP头“Content-Type”必须正确配置响应包的类型，禁止非HTML类型的响应包设置为“text/html”。
2. [推荐] **【推荐】**使用HTTPS进行前后端通信。

## 3.5 文件安全

1. [强制] **【强制】**程序终止前删除临时文件。
2. [强制] **【强制】**检测和处理文件相关的错误。
> **说明：**Java的文件操作方法往往有一个返回值，而不是抛出一个异常，表示失败。因此，忽略文件操作返回值的程序，往往无法检测到这些操作是否失败。Java程序必须检查执行文件I / O方法的返回值。
错误的写法：File file = new File(args[0]);file.delete();正确的写法：File file = new File("file");if (!file.delete()) {log.error("Deletion failed");}
3. [强制] **【强制】**只允许上传满足业务需要的相关文档类型。
4. [强制] **【强制】**通过检查文件报头信息，验证上传文档是否是所期待的类型。
> **说明：**只验证文件类型扩展是不够的。
5. [强制] **【强制】**关闭在文件上传目录的运行权限。
6. [强制] **【强制】**应用程序文件和资源必须是只读的。
7. [强制] **【强制】**避免路径穿越。
> **说明：**判断文件或文件路径参数中是否包含./,../等路径，如果存在则拒绝请求。

## 3.6 内存安全

1. [强制] **【强制】**及时释放资源。
> **说明：**垃圾收集器无法释放非内存资源，如打开的文件描述符与数据库的连接。因此，不释放资源，可能导致资源耗尽攻击。
try {final FileInputStream stream = new FileInputStream(fileName);try {final BufferedReader bufRead =new BufferedReader(new InputStreamReader(stream));String line;while ((line = bufRead.readLine()) != null) {
sendLine(line);}} finally {if (stream != null) {try {stream.close();} catch (IOException e) {// forward to handler}}}} catch (IOException e) {// forward to handler
2. [强制] **【强制】**在序列化过程中避免内存和资源泄漏。
> **说明：**错误的写法：
class SensorData implements Serializable {// 1 MB of data per instance!public static SensorData readSensorData() {...}public static boolean isAvailable() {...}}class SerializeSensorData {public static void main(String[] args) throws IOException {
ObjectOutputStream out = null;try {out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("ser.dat")));while (SensorData.isAvailable()) {// note that each SensorData object is 1 MB in size
SensorData sd = SensorData.readSensorData();out.writeObject(sd);}} finally {if (out != null) {out.close();}}}}正确的写法：class SerializeSensorData {public static void main(String[] args) throws IOException {
ObjectOutputStream out = null;try {out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("ser.dat")));while (SensorData.isAvailable()) {// note that each SensorData object is 1 MB in size
SensorData sd = SensorData.readSensorData();out.writeObject(sd);out.reset(); // reset the stream}} finally {if (out != null) {out.close();}}}}

# 4. 错误码

Java 开发错误码规范如下：1.错误码共共8位，包含三个部分：服务类型（2）、错误类型（2）、数字码（4），使用十六进制格式。 2.服务类型表示每个服务jar包，各项目组申请注册，申请到编码后中心统一维护信息。 3.错误类型定义错误发生的类型，由中心统一定义，包含入参错误、资源调用错误、数据库错误等，需要添加时由中心统一安排。 4.数字码表示具体业务的错误信息，由各服务自行定义记录。