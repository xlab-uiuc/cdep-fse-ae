package assertion;

public class Assert {
  
  private static void dieWithInfo() {
    dieWithInfo("");
  }
  
  private static void dieWithInfo(String msg) {
    System.err.println("[ASSERT] Assert failure: " + msg);
    for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
      if (ste.getMethodName().compareTo("getStackTrace") == 0 ||
          ste.getMethodName().compareTo("dieWithInfo") == 0
          ) {
      } else {
        System.err.println(ste);
      }
    }
    System.exit(-1);
  }
  
  public static void assertTrue(boolean value) {
    if (!value) dieWithInfo();
  }
  
  public static void assertFalse(boolean value) {
    if (value) dieWithInfo();
  }
  
  public static void assertNotEquals(int v1, int v2) {
    if (v1 == v2) dieWithInfo();
  }
  
  public static void assertNotEquals(int v1, int v2, String msg) {
    if (v1 == v2) dieWithInfo(msg);
  }
  
  public static void assertEquals(int v1, int v2) {
    if (v1 != v2) dieWithInfo();
  }
  
  public static void assertEquals(Object o1, Object o2) {
    if (o1 != o2) dieWithInfo();
  }
  
  public static void assertNotEquals(Object o1, Object o2) {
    if (o1 == o2) dieWithInfo();
  }
  
  public static void assertImpossible(String msg) {
    dieWithInfo(msg);
  }

  public static void assertNotNull(Object o) {
    if (o == null) dieWithInfo();
  }
  
  public static void assertNull(Object o) {
    if (o != null) dieWithInfo();
  }
}
