import java.sql.*;
public class TableCounts {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(
        "jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true",
        "root",
        "123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("select table_name, table_rows from information_schema.tables where table_schema = database() order by table_name")) {
      while (rs.next()) {
        System.out.println(rs.getString(1) + "=" + rs.getString(2));
      }
    }
  }
}