package android.os;

/**
 * android.os.Handlerの擬似クラス
 */
public class Handler {
    public void post(Runnable runnable) {
        // Javaアプリケーシヨンでは何もしない
        try {
            runnable.run();
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }
}
