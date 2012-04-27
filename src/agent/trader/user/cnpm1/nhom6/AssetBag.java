package agent.trader.user.cnpm1.nhom6;

import java.util.Vector;

import agent.trader.UserAgent;

public class AssetBag {
	
	public MariaAgent agent;
	public double balance;
	public Vector<Asset> assets;
	
	public AssetBag(MariaAgent agent) {
		this.agent = agent;
		assets = new Vector<Asset>();
	}
	
	public void setBalance(double d) {
		this.balance = d;
	}
	
	public double getBalance() {
		return this.balance;
	}
	
	/**
	 * Add new asset
	 * @param type
	 * @param amount
	 */
	public void addAsset(String type, int amount) {
		Asset asset = new Asset(type, amount, this);
		assets.add(asset);
	}
	
	/**
	 * Get asset object by type
	 * @param type
	 * @return
	 */
	public Asset getAsset(String type) {
		for (int i = 0; i < assets.size(); i++) {
			Asset asset = assets.get(i);
			
			if (asset.getType().equals(type)) {
				return asset;
			}
		}
		return null;
	}

	public Vector<Asset> getAssets() {
		return assets;
	}

	public void setAssets(Vector<Asset> assets) {
		this.assets = assets;
	}
	
}
