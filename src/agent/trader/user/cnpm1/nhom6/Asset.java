package agent.trader.user.cnpm1.nhom6;

import agent.ontology.Metal;

public class Asset {
	
	public AssetBag bag;
	public String type;
	public int amount;
	public double price = 0;
	public double startPrice = 0;
	public int priceUpTimes = 0;
	public int priceDownTimes = 0;
	public double changes = 0;
	
	/**
	 * Constructor setup asset type and amount
	 * @param type
	 * @param amount
	 */
	public Asset(String type, int amount, AssetBag bag) {
		this.type = type;
		this.amount = amount;
		this.bag = bag;
	}
	
	/**
	 * Add n to amount
	 * @param n
	 */
	public void add(int n) {
		amount += n; 
	}

	/**
	 * Remove n to amount
	 * @param n
	 */
	public void remove(int n) {
		amount -= n;
	}
	
	/**
	 * Update price and compare with old price to update price changes
	 * @param newPrice
	 */
	public void updatePrice(double newPrice) {
		if (this.price != 0) {
			if (newPrice > this.price) {
				this.priceUpTimes += 1;
			} else if (newPrice < this.price) {
				this.priceDownTimes += 1;
			}
		} else {
			this.startPrice = newPrice;
			this.bag.agent.slog("Start Price + " + this.type + " - " + this.startPrice);
		}
		if (this.price > 0) {
			this.changes = newPrice - this.price;
		} else {
			this.changes = 0;
		}
		this.price = newPrice;
		this.bag.agent.slog(
				"Price Updated:" +
				"\n " + this.type +
				"\n\tPrice: " + this.price +
				"\n\tChanges: " + this.changes +
				"\n\tUp: " + this.priceUpTimes +
				"\n\tDown: " + this.priceDownTimes +
				"\n\tChanges from start: " + (this.price / this.startPrice - 1)
			);
		//this.bag.agent.slog("Price update\n" + this.type + ": " + this.price + "\n\tChanges: " + this.changes + "\n\tUp: " + this.priceUpTimes + "\tDown: " + this.priceDownTimes);
	}
	
	/**
	 * Get asset type
	 * @return
	 */
	public String getType() {
		return this.type;
	}
	
	/**
	 * Get Metal (to send out)
	 * @return
	 */
	public Metal getMetal() {
		if (this.type.equals("plat")) {
			return Metal.PLATINUM;
		} else if (this.type.equals("gold")) {
			return Metal.GOLD;
		}
		return Metal.SILVER;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}
	
}
