package agent.trader.user.cnpm1.nhom6;

import agent.trader.UserAgent;

public class MariaAgent extends UserAgent {

	public static boolean SELL = true;
	public static boolean BUY = false;

	public MariaAgent(int logLevel) {
		super(logLevel);
	}

	public void slog (String str) {
		System.out.println(this.getName() + ": " + str);
	}
}
