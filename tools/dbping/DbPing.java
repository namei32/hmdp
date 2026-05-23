import java.sql.*;
public class DbPing {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(
        "jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true",
        "root",
        "123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("select database() as db, version() as ver")) {
      while (rs.next()) {
        System.out.println("db=" + rs.getString("db") + "; version=" + rs.getString("ver"));
      }
    }
  }
}