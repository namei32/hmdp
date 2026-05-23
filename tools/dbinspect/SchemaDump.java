import java.sql.*;
public class SchemaDump {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(
        "jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true",
        "root",
        "123456")) {
      dumpTables(c);
      System.out.println("=== FOREIGN KEYS ===");
      dumpForeignKeys(c);
    }
  }

  private static void dumpTables(Connection c) throws Exception {
    String sql = "select table_name from information_schema.tables where table_schema = database() order by table_name";
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        String table = rs.getString(1);
        System.out.println("=== TABLE " + table + " ===");
        dumpColumns(c, table);
      }
    }
  }

  private static void dumpColumns(Connection c, String table) throws Exception {
    String sql = "select column_name, column_type, is_nullable, column_key, extra from information_schema.columns where table_schema = database() and table_name = ? order by ordinal_position";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, table);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | null=" + rs.getString(3) + " | key=" + rs.getString(4) + " | extra=" + rs.getString(5));
        }
      }
    }
  }

  private static void dumpForeignKeys(Connection c) throws Exception {
    String sql = "select table_name, column_name, referenced_table_name, referenced_column_name, constraint_name from information_schema.key_column_usage where table_schema = database() and referenced_table_name is not null order by table_name, ordinal_position";
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      boolean any = false;
      while (rs.next()) {
        any = true;
        System.out.println(rs.getString(1) + "." + rs.getString(2) + " -> " + rs.getString(3) + "." + rs.getString(4) + " (" + rs.getString(5) + ")");
      }
      if (!any) {
        System.out.println("<none>");
      }
    }
  }
}