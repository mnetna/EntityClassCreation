import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

//import org.springframework.util.StopWatch;

public class EntityClassCreation {
  private final static String JDBC_URL = "jdbc:oracle:thin:@10.10.1.160:1521";
  private final static String JDBC_USERID = "";
  private final static String JDBC_PASSWORD = "";
  
  public static void main(String[] args) throws Exception {
//    StopWatch stopWatch = new StopWatch();
//    stopWatch.start();
    
    List<String> tableNameList = null;
    try (Connection con = DriverManager.getConnection(JDBC_URL, JDBC_USERID, JDBC_PASSWORD)) {
       DatabaseMetaData dbmeta = con.getMetaData();
       tableNameList = getResultSet(dbmeta.getTables(null, JDBC_USERID, null, new String[]{"TABLE"}), (ResultSet rs) -> rs.getString("TABLE_NAME"));
       
       for (String tableName : tableNameList) {
         List<String> pkList = getResultSet(dbmeta.getPrimaryKeys(null, JDBC_USERID, tableName), (ResultSet rs) -> rs.getString("COLUMN_NAME"));
         List<EntityVariable> entityVariableList = getResultSet(dbmeta.getColumns(null, JDBC_USERID, tableName, null), (ResultSet rs) ->
           new EntityVariable(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"), rs.getString("REMARKS"), pkList.contains(rs.getString("COLUMN_NAME"))));
         writeEntityFile(tableName, entityVariableList, false);
       }
    }
    
//    stopWatch.stop();
//    System.out.println("Total time... {"+stopWatch.getTotalTimeMillis()+"}");
    
  }

  @FunctionalInterface
  interface FunctionWithException<T, R, E extends SQLException> {
    R apply(T t) throws E;
  }

  private static <T> List<T> getResultSet(ResultSet rs, FunctionWithException<ResultSet, T, SQLException> function) throws SQLException {
    List<T> result = new ArrayList<T>();
    while (rs.next()) {
      result.add((T) function.apply(rs));
    }
    rs.close();
    return result;
  }

  private static void writeEntityFile(String tableName, List<EntityVariable> entityVariableList, boolean isPK) throws Exception {
    String fileName = convertUnderscoreNameToPropertyName(
        tableName.startsWith("SEBI_") ? tableName.replaceFirst("SEBI_", "") : tableName, true);
    if (isPK) {fileName = fileName.concat("PK");}
    Path path = Paths.get("F:/entity/" + fileName + ".java");
    Files.createFile(path);

    final FileWriter fileWriter = new FileWriter(path.toFile());
    final PrintWriter printWriter = new PrintWriter(fileWriter);

    try {
      printWriter.println("package com.hanafn.eai.entity;");
      printWriter.println("import java.io.Serializable;");
      printWriter.println("import javax.persistence.Column;");
      printWriter.println("import javax.persistence.Entity;");
      printWriter.println("import javax.persistence.Id;");
      printWriter.println("import javax.persistence.Table;");
      printWriter.println();
      
      if (!isPK) {printWriter.println("@Entity");}
      if (!isPK) {printWriter.printf("@Table(name= \"%s\")\n", tableName.toUpperCase());}
      printWriter.printf("public class %s implements Serializable {\n", fileName);
      printWriter.println();
      
      entityVariableList.stream().forEach(x -> {
        if (!isPK && x.isPK()) {printWriter.println("@Id");}
        if (!isPK) {printWriter.printf("@Column(name = \"%s\")\n", x.getColumnName());}
        printWriter.printf("private %s %s;\n", x.getColumnType(), x.getColumnNameCamel());
      });
      printWriter.println();
      
      entityVariableList.stream().forEach(x -> {
        printWriter.printf("public %s get%s() {\nreturn %s;\n}\n", x.getColumnType(), x.getColumnNamePascal(),
            x.getColumnNameCamel());
        printWriter.printf("public void set%s(%s %s) {\nthis.%s = %s;\n}\n", x.getColumnNamePascal(), x.getColumnType(),
            x.getColumnNameCamel(), x.getColumnNameCamel(), x.getColumnNameCamel());
      });
      printWriter.print("}");
    } finally {
      if (printWriter != null) {
        printWriter.close();
      }
      if (fileWriter != null) {
        fileWriter.close();
      }
    }

    if (!isPK) {
      List<EntityVariable> pkList = entityVariableList.stream().filter(x -> x.isPK()).collect(Collectors.toList());
      if (pkList.size() >= 2) {
        writeEntityFile(tableName, pkList, true);
      }
    }
  }

  public static String convertUnderscoreNameToPropertyName(String name, boolean isPascal) {
    StringBuilder result = new StringBuilder();
    boolean nextIsUpper = false;
    if (name != null && name.length() > 0) {
      if (isPascal) {
        result.append(Character.toUpperCase(name.charAt(0)));
      } else {
        if (name.length() > 1 && name.charAt(1) == '_') {
          result.append(Character.toUpperCase(name.charAt(0)));
        } else {
          result.append(Character.toLowerCase(name.charAt(0)));
        }
      }
      for (int i = 1; i < name.length(); i++) {
        char c = name.charAt(i);
        if (c == '_') {
          nextIsUpper = true;
        } else {
          if (nextIsUpper) {
            result.append(Character.toUpperCase(c));
            nextIsUpper = false;
          } else {
            result.append(Character.toLowerCase(c));
          }
        }
      }
    }
    return result.toString();
  }
}

class EntityVariable {
  private String columnName;
  private String columnNameCamel;
  private String columnNamePascal;
  private String columnType;
  private String comment;
  private boolean isPK;

  public EntityVariable(String columnName, String columnType, String comment, boolean isPK) {
    this.columnName = columnName;
    this.columnNameCamel = EntityClassCreation.convertUnderscoreNameToPropertyName(columnName, false);
    this.columnNamePascal = EntityClassCreation.convertUnderscoreNameToPropertyName(columnName, true);
    this.columnType = Type.fromDbType(columnType).getJavaType();
    this.comment = comment;
    this.isPK = isPK;
  }

  public String getColumnName() {
    return columnName;
  }

  public String getColumnNameCamel() {
    return columnNameCamel;
  }

  public String getColumnNamePascal() {
    return columnNamePascal;
  }

  public String getColumnType() {
    return columnType;
  }

  public String getComment() {
    return comment;
  }

  public boolean isPK() {
    return isPK;
  }
  
  enum Type {
    VARCHAR("String"), CHAR("char"), INT("int"), TIMESTAMP("timestamp"), DATE("Date");

    private String javaType;

    Type(String javaType) {
      this.javaType = javaType;
    }

    public String getJavaType() {
      return javaType;
    }

    public static Type fromDbType(String javaType) {
      switch (javaType) {
        case "VARCHAR2" :
        case "NVARCHAR2" : {
          return VARCHAR;
        }
        case "CHAR" : {
          return CHAR;
        }
        case "NUMBER" : {
          return INT;
        }
        case "TIMESTAMP(6)" : {
          return TIMESTAMP;
        }
        case "DATE" : {
          return DATE;
        }
        default : {
          return VARCHAR;
        }
      }
    }
  }
}