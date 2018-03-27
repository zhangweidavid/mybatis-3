/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

/**
 * 通用标记解析器
 * @author Clinton Begin
 */
public class GenericTokenParser {
  //标记开始符号
  private final String openToken;
  //标记结束符号
  private final String closeToken;
  //标记处理器
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 对指定的字符串进行标记处理
   * @param text
   * @return
   */
  public String parse(String text) {
    //如果字符串为null,或者为空则直接返回一个空字符串
    if (text == null || text.isEmpty()) {
      return "";
    }
    // 查找开始标记符，如果不存在则直接返回
    int start = text.indexOf(openToken, 0);
    if (start == -1) {
      return text;
    }
    //将字符串转换为字符数组
    char[] src = text.toCharArray();
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    //遍历
    while (start > -1) {
      //如果标记符的开始符号存在且标记符的前一个字符是'\\'
      if (start > 0 && src[start - 1] == '\\') {
        //这个标记符被转意了，删除转意符继续操作
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        //找到了标记符的开始符号，开始查找到结束符号，如果expression为null则初始化
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }//第一次就是将开始到标记符开始符号之间，两个标记符之间或标记符到结尾的的字符串追加到builder,
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();//便宜量为start+开始标记的长度
        int end = text.indexOf(closeToken, offset);//从偏移量向后找到第一个结尾标记符
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            expression.append(src, offset, end - offset);//标记符中的字符串存入到表达式中
            offset = end + closeToken.length();//偏移量继续后移
            break;
          }
        }
        if (end == -1) {
          //如果没有找到结束标记则将start到字符串的结尾都全部追加到builder
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          //有结结束标记，则对占位标记符进行处理，并将处理的结果添加到builder
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      //继续寻找下一个标记符
      start = text.indexOf(openToken, offset);
    }
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
