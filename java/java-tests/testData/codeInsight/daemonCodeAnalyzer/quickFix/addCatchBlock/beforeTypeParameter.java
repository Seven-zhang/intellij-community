// "Surround with try/catch" "true"
class Test {

  interface I<E extends Exception> {
    void call() throws E;
  }

  public static <E extends Exception> void method(I<E> i) {
    i.ca<caret>ll();
  }
}