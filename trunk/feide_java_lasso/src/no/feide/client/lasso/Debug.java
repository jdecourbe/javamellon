package no.feide.client.lasso;

/**
 * This is a dummy-class which stands in for a proper logging class.
 */
public class Debug {

    public static Debug getInstance() {
        return new Debug();
    }

	public boolean messageEnabled() {
		return true;
	}

	public void message(String string) {
		System.out.println(string);
	}

	public boolean warningEnabled() {
		return true;
	}

	public void warning(String string) {
		System.out.println(string);
	}

	public void error(String string) {
		System.err.println(string);
	}

}
