package agent.trader.user.cnpm1.nhom3;

import jade.content.abs.AbsPredicate;
import jade.content.onto.BasicOntology;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;
import jade.proto.SimpleAchieveREInitiator;

import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Vector;

import agent.ontology.AgentID;
import agent.ontology.AuctionRequest;
import agent.ontology.Bid;
import agent.ontology.CurrentAccount;
import agent.ontology.CurrentMarketPrice;
import agent.ontology.Deal;
import agent.ontology.FutureTrend;
import agent.ontology.GetAccountInfo;
import agent.ontology.GetLastDeal;
import agent.ontology.GetMarketPrice;
import agent.ontology.Good;
import agent.ontology.Metal;
import agent.ontology.TradingOntology;
import agent.ontology.WantTo;
import agent.trader.UserAgent;

public class Nhom3 extends UserAgent {
	private static final int INFO_UPDATE_INTERVAL_TIME = 50;
	private static final int LINEAR_CHECK_INTERVAL_TIME = INFO_UPDATE_INTERVAL_TIME * 4;
	private static final int LINEAR_SAMPLE_NUMBER = 5;
	private static final double BIDDING_PERCENT_BUY = 0.08;

	private static final Random RANDOM = new Random();

	// Market value
	private double mPlaPrice;
	private double mGolPrice;
	private double mSilPrice;

	// Current account
	private double mMoney;
	private int mPlaQuan;
	private int mGolQuan;
	private int mSilQuan;

	// Current Future Trend
	private FutureTrend mCurrentFutureTrend;

	// Last agreed deal in the market
	private Deal mLastAgreedDeal;
	
	// Hung: History variable
	private Vector<Double> hPlaPrice = new Vector<Double>();
	private Vector<Double> hGolPrice = new Vector<Double>();
	private Vector<Double> hSilPrice = new Vector<Double>();
	private String[] cTrend = {"Stable", "Stable", "Stable"};
	private boolean[] detectTrend = {false, false, false};
	private double mPlatinumTrend = 0;
	private double mGoldTrend = 0;
	private double mSilverTrend = 0;
	

	public Nhom3() {
		super(INFO);

		mPlaPrice = 0;
		mGolPrice = 0;
		mSilPrice = 0;
	}

	@Override
	protected void setup() {
		super.setup();

		// Register language and ontology
		mManager.registerLanguage(mCodec);
		mManager.registerOntology(mOntology);

		// Listen to price update

		// Check price for the first time
		addBehaviour(new RequestMarketPriceBehaviour(this));

		// Check last agreed deal
		addBehaviour(new WakerBehaviour(this, 500) {
			@Override
			protected void onWake() {
				addBehaviour(new RequestToGetLastDealBehaviour(myAgent));
			}
		});

		addBehaviour(new WakerBehaviour(this, 500) {
			@Override
			protected void onWake() {
				addBehaviour(new RequestCurrentAccountBehaviour(myAgent));
			}
		});

		addBehaviour(new UpdateMarketInfoListener());

		MessageTemplate senderMt = MessageTemplate.MatchSender(mBrokerService);
		MessageTemplate cfpMt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
		MessageTemplate acceptMt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
		MessageTemplate rejectMt = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
		MessageTemplate mt = MessageTemplate.and(senderMt, MessageTemplate.or(cfpMt, MessageTemplate.or(acceptMt, rejectMt)));

		addBehaviour(new BiddingBehaviour(this, mt));
		
		addBehaviour(new TickerBehaviour(this, LINEAR_CHECK_INTERVAL_TIME) {
			
			@Override
			protected void onTick() {
				// TODO Auto-generated method stub
				addBehaviour(new DetectPlatiumForcastTrend(myAgent));
				addBehaviour(new DetectGoldForcastTrend(myAgent));
				addBehaviour(new DetectSilverForcastTrend(myAgent));
			}
		});
		

		// This behaviour will have a chance of 50% of sending out request to
		// buy or sell a random amount of random metal with threshold is set to
		// market price +/- 10% every 100 ms
		// Just here to show how a request could be sent to actively sell or buy
		// metal
		addBehaviour(new TickerBehaviour(this, INFO_UPDATE_INTERVAL_TIME) {

			@Override
			protected void onTick() {
				// Check the market price first
				addBehaviour(new RequestMarketPriceBehaviour(myAgent));
				/*
				if (RANDOM.nextInt(2) == 0) {
					// Wait 2 secs
					addBehaviour(new WakerBehaviour(myAgent, 200) {
						@Override
						protected void onWake() {
							// TODO: NEEDED TO BE IMPLEMENTED
							// Start an random auction

							// Sell or buy
							boolean wantToSell = RANDOM.nextBoolean();

							// Which metal
							Metal metal = null;
							// Threshold price
							double threshold = 0;
							double mr = RANDOM.nextDouble();
							if (mr > 0.33) {
								metal = Metal.PLATINUM;
								threshold = mPlaPrice;
							} else if (mr > 0.66) {
								metal = Metal.GOLD;
								threshold = mGolPrice;
							} else {
								metal = Metal.SILVER;
								threshold = mSilPrice;
							}
							
							//log(INFO, hPlaPrice.elementAt(hPlaPrice.size() - 1) + " " + hPlaPrice.elementAt(hPlaPrice.size() - 2));

							// How many unit (1 - 10)
							int quantity = RANDOM.nextInt(10) + 1;

							// Threshold value
							// -10% if sell
							// +10% if buy
							// * quantity to have the total value
							if (wantToSell)
								threshold = (threshold * (1 - RANDOM.nextDouble() * 0.1)) * quantity;
							else
								threshold = (threshold * (1 + RANDOM.nextDouble() * 0.1)) * quantity;

							AuctionRequest auction = new AuctionRequest(new AgentID(getAID().getName()), new WantTo(wantToSell, new Good(metal, quantity)), threshold);
							addBehaviour(new RequestAuctionBehaviour(myAgent, auction));
						}
					});
				}
				*/
			}
		});
	}
	
	// HUNG FUNCTIONS
	private static double linearRegression(double[] y, int n) { 
        int MAXN = 1000;
        double[] x = new double[MAXN];
        
        // first pass: read in data, compute xbar and ybar
        double sumx = 0.0, sumy = 0.0, sumx2 = 0.0;
        for (int i = 0; i < n; i++) {
            x[i] = i + 1;
            sumx  += x[i];
            sumx2 += x[i] * x[i];
            sumy  += y[i];
        }
        double xbar = sumx / n;
        double ybar = sumy / n;

        // second pass: compute summary statistics
        double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
        for (int i = 0; i < n; i++) {
            xxbar += (x[i] - xbar) * (x[i] - xbar);
            yybar += (y[i] - ybar) * (y[i] - ybar);
            xybar += (x[i] - xbar) * (y[i] - ybar);
        }
        double beta1 = xybar / xxbar;
        double beta0 = ybar - beta1 * xbar;

        // print results
        return beta1;
    }
	
	// HUNG BEHAVIOUR
	class DetectPlatiumForcastTrend extends OneShotBehaviour {
		// Constructor
		public DetectPlatiumForcastTrend(Agent myAgent) {
			super(myAgent);
		}

		@Override
		public void action() {
			if (cTrend[0].equals("Stable") || detectTrend[0]) {
				mPlatinumTrend = 0;
				return;
			}
			int operate = (cTrend[0].equals("Up") ? 1 : -1);
			int n = hPlaPrice.size();
			int m = 0;
			double[] parttern = new double[100];
			
			if (n > 3) {
				int i = n - 1;
				for (; i >= 1; i--) {
					if (operate * ((double)hPlaPrice.elementAt(i) - (double)hPlaPrice.elementAt(i - 1)) < 0) break;
					parttern[m++] = (double) hPlaPrice.elementAt(i);
					if (m > LINEAR_SAMPLE_NUMBER) break;
				}
				if (m > 3) {
					mPlatinumTrend = - linearRegression(parttern, m);
					hPlaPrice.clear();
					detectTrend[0] = true;
					//log(INFO, "Platinum Trend " + cTrend[0] + ": " + mPlatinumTrend);
					addBehaviour(new DecideAuctionBehaviour(myAgent, cTrend[0].equals("Down"), Metal.PLATINUM));
				}
			}
		}
	}
	
	class DetectGoldForcastTrend extends OneShotBehaviour {
		// Constructor
		public DetectGoldForcastTrend(Agent myAgent) {
			super(myAgent);
		}

		@Override
		public void action() {
			
			if (cTrend[1].equals("Stable") || detectTrend[1]) {
				return;
			}
			
			int operate = (cTrend[1].equals("Up") ? 1 : -1);
			int n = hGolPrice.size();
			int m = 0;
			double[] parttern = new double[100];
			
			if (n > 3) {
				int i = n - 1;
				for (; i >= 1; i--) {
					if (operate * ((double)hGolPrice.elementAt(i) - (double)hGolPrice.elementAt(i - 1)) < 0) break;
					parttern[m++] = (double) hGolPrice.elementAt(i);
					if (m > LINEAR_SAMPLE_NUMBER) break;
				}
				if (m > 3) {
					mGoldTrend = - linearRegression(parttern, m);
					hGolPrice.clear();
					detectTrend[1] = true;
					//log(INFO, "Gold Trend " + cTrend[1] + ": " + mGoldTrend);
					addBehaviour(new DecideAuctionBehaviour(myAgent, cTrend[1].equals("Down"), Metal.GOLD));
				}
			}
		}
	}
	
	class DetectSilverForcastTrend extends OneShotBehaviour {
		// Constructor
		public DetectSilverForcastTrend(Agent myAgent) {
			super(myAgent);
		}

		@Override
		public void action() {
			
			if (cTrend[2].equals("Stable") || detectTrend[2]) {
				return;
			}
			
			int operate = (cTrend[2].equals("Up") ? 1 : -1);
			int n = hSilPrice.size();
			int m = 0;
			double[] parttern = new double[100];
			boolean isTrend = false;
			
			if (n > 3) {
				int i = n - 1;
				for (; i >= 1; i--) {
					if (operate * ((double)hSilPrice.elementAt(i) - (double)hSilPrice.elementAt(i - 1)) < 0) break;
					parttern[m++] = (double) hSilPrice.elementAt(i);
					if (m > LINEAR_SAMPLE_NUMBER) break;
				}
				if (m > 3) {
					mSilverTrend = - linearRegression(parttern, m);
					hSilPrice.clear();
					detectTrend[2] = true;
					//log(INFO, "Silver Trend " + cTrend[2] + ": " + mSilverTrend);
					addBehaviour(new DecideAuctionBehaviour(myAgent, cTrend[2].equals("Down"), Metal.SILVER));
				}
			}
		}
	}
	
	class DecideAuctionBehaviour extends OneShotBehaviour {
		private Metal metal;
		private boolean wantToSell;
		private double buyPercent = 0.1;
		private double sellPercent = 0.1;

		// Constructor
		public DecideAuctionBehaviour(Agent myAgent, boolean sell, Metal mt) {
			super(myAgent);
			metal = mt;
			wantToSell = sell;
		}
				
		@Override
		public void action() {
			// TODO Auto-generated method stub

			// How many unit (1 - 10)
			int Total = 0;
			double price = 0;
			double threshold = 0;
			
			if (metal == Metal.PLATINUM) {
				Total = mPlaQuan;
				price = mPlaPrice;
			} if (metal == Metal.GOLD) {
				Total = mGolQuan;
				price = mGolPrice;
			} if (metal == Metal.SILVER) {
				Total = mSilQuan;
				price = mSilPrice;
			}
	
			// Threshold value
			// -10% if sell
			// +10% if buy
			// * quantity to have the total value
			int quantity = 0;
			if (wantToSell) {
				quantity = Total / 2;
				threshold = (price * (1 - sellPercent)) * quantity;
			} else {
				quantity = (int) ((double) mMoney / (price * (1 + buyPercent) * 2));
				threshold = (price * (1 + buyPercent)) * quantity;
			}
			log(INFO, "Threshold : " + threshold);
			AuctionRequest auction = new AuctionRequest(new AgentID(getAID().getName()), new WantTo(wantToSell, new Good(metal, quantity)), threshold);
			addBehaviour(new RequestAuctionBehaviour(myAgent, auction));
		
		}
		
	}

	// AGENT BEHAVIOUR

	/**
	 * This behaviour send out a request for a current market price
	 * Register a behaviour to listen for info from the info agent
	 */
	class RequestMarketPriceBehaviour extends OneShotBehaviour {
		// Constructor
		public RequestMarketPriceBehaviour(Agent myAgent) {
			super(myAgent);
		}

		@Override
		public void action() {
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			request.addReceiver(mInfoService);
			request.setOntology(mOntology.getName());
			request.setLanguage(mCodec.getName());
			try {
				GetMarketPrice gmp = new GetMarketPrice();
				Action action = new Action(mInfoService, gmp);
				getContentManager().fillContent(request, action);
				send(request);
			} catch (Exception e) {
				log(e);
			}
		}
	}

	/**
	 * Inner class UpdateMarketPriceListener.
	 * This is the behaviour used by Trader agents to listen for market price
	 * update from info agent
	 * New update will be update directly to the local variable
	 */
	private class UpdateMarketInfoListener extends CyclicBehaviour {
		@Override
		public void action() {
			// Create template to catch message from info agent
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(mInfoService));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					AbsPredicate cs = (AbsPredicate) myAgent.getContentManager().extractAbsContent(msg);
					if (cs.getTypeName().equals(TradingOntology.CURRENT_MARKET_PRICE)) {
						// Update current market price
						CurrentMarketPrice cmp = (CurrentMarketPrice) mOntology.toObject(cs);
						mPlaPrice = cmp.getPlatinumPrice();
						mGolPrice = cmp.getGoldPrice();
						mSilPrice = cmp.getSilverPrice();
						
						// Hung: Price history.
						hPlaPrice.addElement(mPlaPrice);
						hGolPrice.addElement(mGolPrice);
						hSilPrice.addElement(mSilPrice);
						// Hung: Price history End

						log(FINE, "Current market price obtainted:\t" + //
						"\tPlatinum Price: " + mPlaPrice + //
						"\tGold Price: " + mGolPrice + //
						"\tSilver Price: " + mSilPrice);//
					} else if (cs.getTypeName().equals(TradingOntology.FUTURE_TREND)) {
						// Update future trend
						FutureTrend trend = (FutureTrend) mOntology.toObject(cs);

						// Update new trend
						mCurrentFutureTrend = trend;
						
						// Hung: Trend
						if (trend.getMetal().equals(Metal.PLATINUM)) {
							cTrend[0] = trend.getDirection().getDirectionCode();
							detectTrend[0] = false;
						} else if (trend.getMetal().equals(Metal.GOLD)) {
							cTrend[1] = trend.getDirection().getDirectionCode();
							detectTrend[1] = false;
						} else if (trend.getMetal().equals(Metal.SILVER)) {
							cTrend[2] = trend.getDirection().getDirectionCode();
							detectTrend[2] = false;
						}
						// Hung: Trend End

						log(FINE, "Future market Trend obtainted:\t" + //
						"\tMetal: " + trend.getMetal() + //
						"\tDirection: " + trend.getDirection());//
					} else {
						// Unexpected response received from the info agent.
						log(SEVERE, "Unexpected response from " + msg.getSender().getName());
					}

				} // End of try
				catch (Exception e) {
					log(e);
				}
			} else {
				block();
			}
		}
	} // End of inner class OfferRequestsServer

	/**
	 * This behaviour send a request to complete a deal to the bank
	 * Register a behaviour to listen to the confirmation
	 */
	class RequestToCompleteADealBehaviour extends SequentialBehaviour {
		private final Deal mDeal;

		// Constructor
		public RequestToCompleteADealBehaviour(Agent myAgent, Deal deal) {
			super(myAgent);
			mDeal = deal;
		}

		@Override
		public void onStart() {
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			request.addReceiver(mBankService);
			request.setOntology(mOntology.getName());
			request.setLanguage(mCodec.getName());

			try {
				Action actExpr = new Action(mBankService, mDeal);
				getContentManager().fillContent(request, actExpr);

				// Create and add a behaviour to listen for result account
				// coming back
				addSubBehaviour(new HandleInformCompleteADealBehaviour(myAgent, request));
			} catch (Exception e) {
				log(e);
			}
		}
	}

	/**
	 * This behaviour check for confirmation of current account.
	 * This is done following a FIPA-Request interaction protocol
	 */
	class HandleInformCompleteADealBehaviour extends SimpleAchieveREInitiator {
		// Constructor
		public HandleInformCompleteADealBehaviour(Agent myAgent, ACLMessage queryMsg) {
			super(myAgent, queryMsg);
			queryMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
		}

		@Override
		protected void handleInform(ACLMessage msg) {
			try {
				AbsPredicate absResult = (AbsPredicate) myAgent.getContentManager().extractAbsContent(msg);
				if (absResult.getTypeName().equals(TradingOntology.CURRENT_ACCOUNT)) {
					// Check the updated account
					CurrentAccount account = (CurrentAccount) mOntology.toObject(absResult);

					// Update current account
					mMoney = account.getMoney();
					mPlaQuan = account.getPlatinumQuantity();
					mGolQuan = account.getGoldQuantity();
					mSilQuan = account.getSilverQuantity();

					// Print updated account
					log(FINE, "Current Account:" + // 
					"\n\t Money: " + account.getMoney() + // 
					"\n\t Platinum Quantity: " + account.getPlatinumQuantity() + // 
					"\n\t Gold Quantity: " + account.getGoldQuantity() + //
					"\n\t Silver Quantity: " + account.getSilverQuantity());//
				} else {
					// Unexpected response received from the info agent.
					log(SEVERE, "Unexpected response from " + msg.getSender().getName());
				}
			} // End of try
			catch (Exception e) {
				log(e);
			}
		}

		@Override
		protected void handleFailure(ACLMessage msg) {
			log(FINE, "Complete a deal action has been a failure because: " + msg.getContent());
		}

		@Override
		protected void handleRefuse(ACLMessage msg) {
			log(FINE, "Complete a deal action has been a rejected because: " + msg.getContent());
		}
	}

	/**
	 * This behaviour send a request to start an auction to the auction agent
	 * Register a behaviour to listen to the confirmation
	 */
	class RequestAuctionBehaviour extends SequentialBehaviour {
		private final AuctionRequest mAuctionRequest;

		// Constructor
		public RequestAuctionBehaviour(Agent myAgent, AuctionRequest auctionRequest) {
			super(myAgent);
			mAuctionRequest = auctionRequest;
		}

		@Override
		public void onStart() {
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			request.addReceiver(mBrokerService);
			request.setOntology(mOntology.getName());
			request.setLanguage(mCodec.getName());

			try {
				Action actExpr = new Action(mBrokerService, mAuctionRequest);
				getContentManager().fillContent(request, actExpr);

				log(FINE, "Request Auction:" + //
				"\n\t Sell: " + mAuctionRequest.getWantTo().getWantToSell() + //
				"\n\t Metal: " + mAuctionRequest.getWantTo().getGood().getMetal().getMetalCode() + //
				"\n\t Quantity: " + mAuctionRequest.getWantTo().getGood().getQuantity() + //
				"\n\t Threshold: " + mAuctionRequest.getThresholdValue());

				// Create and add a behaviour to listen for buyer and price
				// coming back
				addSubBehaviour(new HandleCompletedAuctionBehaviour(myAgent, request));
			} catch (Exception e) {
				log(e);
			}
		}
	}

	/**
	 * This behaviour check for confirmation of completing an auction.
	 * This is done following a FIPA-Request interaction protocol
	 */
	class HandleCompletedAuctionBehaviour extends SimpleAchieveREInitiator {
		// Constructor
		public HandleCompletedAuctionBehaviour(Agent myAgent, ACLMessage queryMsg) {
			super(myAgent, queryMsg);
			queryMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
		}

		@Override
		protected void handleInform(ACLMessage msg) {
			try {
				AbsPredicate absResult = (AbsPredicate) myAgent.getContentManager().extractAbsContent(msg);
				if (absResult.getTypeName().equals(BasicOntology.RESULT)) {
					// Check the deal
					Result result = (Result) mOntology.toObject(absResult);
					if (result.getValue() instanceof Deal) {
						Deal deal = (Deal) result.getValue();

						// Print agreed deal
						log(FINE, "Deal agreed:" + //
						"\n\t DealId: " + deal.getDealId() + // 
						"\n\t Seller: " + deal.getSeller().getName() + // 
						"\n\t Buyer: " + deal.getBuyer().getName() + //
						"\n\t Metal: " + deal.getGood().getMetal().getMetalCode() + // 
						"\n\t Quantity: " + deal.getGood().getQuantity() + //
						"\n\t Value: " + deal.getValue());//

						// Add behavior to complete the deal
						addBehaviour(new RequestToCompleteADealBehaviour(myAgent, deal));
					} else {
						// Unexpected response received from the info agent.
						log(SEVERE, "Unexpected response from " + msg.getSender().getName());
					}
				} else {
					// Unexpected response received from the info agent.
					log(SEVERE, "Unexpected response from " + msg.getSender().getName());
				}

			} // End of try
			catch (Exception e) {
				log(e);
			}
		}

		@Override
		protected void handleFailure(ACLMessage msg) {
			log(FINE, "Complete an auction action has been a failure because: " + msg.getContent());
		}

		@Override
		protected void handleRefuse(ACLMessage msg) {
			log(FINE, "Complete an auction action has been a rejected because: " + msg.getContent());
		}
	}

	/**
	 * This behaviour send a request to complete a deal to the bank
	 * Register a behaviour to listen to the confirmation
	 */
	class RequestCurrentAccountBehaviour extends SequentialBehaviour {
		// Constructor
		public RequestCurrentAccountBehaviour(Agent myAgent) {
			super(myAgent);
		}

		@Override
		public void onStart() {
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			request.addReceiver(mBankService);
			request.setOntology(mOntology.getName());
			request.setLanguage(mCodec.getName());

			GetAccountInfo gai = new GetAccountInfo();

			try {
				Action action = new Action(mBankService, gai);
				getContentManager().fillContent(request, action);

				// Create and add a behaviour to listen for result account
				// coming back
				addSubBehaviour(new HandleInformCurrentAccountBehaviour(myAgent, request));
			} catch (Exception e) {
				log(e);
			}
		}
	}

	/**
	 * This behaviour check for confirmation of current account.
	 * This is done following a FIPA-Request interaction protocol
	 */
	class HandleInformCurrentAccountBehaviour extends SimpleAchieveREInitiator {
		// Constructor
		public HandleInformCurrentAccountBehaviour(Agent myAgent, ACLMessage queryMsg) {
			super(myAgent, queryMsg);
			queryMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
		}

		@Override
		protected void handleInform(ACLMessage msg) {
			try {
				AbsPredicate absResult = (AbsPredicate) myAgent.getContentManager().extractAbsContent(msg);
				if (absResult.getTypeName().equals(TradingOntology.CURRENT_ACCOUNT)) {
					// Check the updated account
					CurrentAccount account = (CurrentAccount) mOntology.toObject(absResult);

					// Update current account
					mMoney = account.getMoney();
					mPlaQuan = account.getPlatinumQuantity();
					mGolQuan = account.getGoldQuantity();
					mSilQuan = account.getSilverQuantity();

					// Print updated account
					log(FINE, "Current Account:" + // 
					"\n\t Money: " + account.getMoney() + // 
					"\n\t Platinum Quantity: " + account.getPlatinumQuantity() + // 
					"\n\t Gold Quantity: " + account.getGoldQuantity() + //
					"\n\t Silver Quantity: " + account.getSilverQuantity());//
				} else {
					// Unexpected response received from the info agent.
					log(SEVERE, "Unexpected response from " + msg.getSender().getName());
				}
			} // End of try
			catch (Exception e) {
				log(e);
			}
		}

		@Override
		protected void handleFailure(ACLMessage msg) {
			log(FINE, "Get current account action has been a failure because: " + msg.getContent());
		}

		@Override
		protected void handleRefuse(ACLMessage msg) {
			log(FINE, "Get current account action has been a rejected because: " + msg.getContent());
		}
	}

	/**
	 * This behaviour send a request to ask for the last agreed deal
	 * Register a behaviour to listen to the confirmation
	 */
	class RequestToGetLastDealBehaviour extends SequentialBehaviour {

		// Constructor
		public RequestToGetLastDealBehaviour(Agent myAgent) {
			super(myAgent);
		}

		@Override
		public void onStart() {
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			request.addReceiver(mBrokerService);
			request.setOntology(mOntology.getName());
			request.setLanguage(mCodec.getName());

			try {
				Action actExpr = new Action(mBrokerService, new GetLastDeal());
				getContentManager().fillContent(request, actExpr);

				// Create and add a behaviour to listen for result account
				// coming back
				addSubBehaviour(new HandleInformLastDealBehaviour(myAgent, request));
			} catch (Exception e) {
				log(e);
			}
		}
	}

	/**
	 * This behaviour check for last agreed deal in the market.
	 * This is done following a FIPA-Request interaction protocol
	 */
	class HandleInformLastDealBehaviour extends SimpleAchieveREInitiator {
		// Constructor
		public HandleInformLastDealBehaviour(Agent myAgent, ACLMessage queryMsg) {
			super(myAgent, queryMsg);
			queryMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
		}

		@Override
		protected void handleInform(ACLMessage msg) {
			try {
				AbsPredicate absResult = (AbsPredicate) myAgent.getContentManager().extractAbsContent(msg);
				if (absResult.getTypeName().equals(BasicOntology.RESULT)) {
					// Check the deal
					Result result = (Result) mOntology.toObject(absResult);
					if (result.getValue() instanceof Deal) {
						Deal deal = (Deal) result.getValue();

						// Print last agreed deal
						log(FINE, "Last agreed deal:" + //
						"\n\t DealId: " + deal.getDealId() + // 
						"\n\t Seller: " + deal.getSeller().getName() + // 
						"\n\t Buyer: " + deal.getBuyer().getName() + //
						"\n\t Metal: " + deal.getGood().getMetal().getMetalCode() + // 
						"\n\t Quantity: " + deal.getGood().getQuantity() + //
						"\n\t Value: " + deal.getValue());//

						// Update last agreed deal in the whole market
						mLastAgreedDeal = deal;
					} else {
						// Unexpected response received from the info agent.
						log(SEVERE, "Unexpected response from " + msg.getSender().getName());
					}
				} else {
					// Unexpected response received from the info agent.
					log(SEVERE, "Unexpected response from " + msg.getSender().getName());
				}
			} // End of try
			catch (Exception e) {
				log(e);
			}
		}

		@Override
		protected void handleFailure(ACLMessage msg) {
			log(FINE, "Complete a deal action has been a failure because: " + msg.getContent());
		}

		@Override
		protected void handleRefuse(ACLMessage msg) {
			log(FINE, "Complete a deal action has been a rejected because: " + msg.getContent());
		}
	}

	/**
	 * Behaviour to handle bidding process
	 * 
	 */
	class BiddingBehaviour extends ContractNetResponder {
		public BiddingBehaviour(Agent myAgent, MessageTemplate mt) {
			super(myAgent, mt);
		}

		@Override
		protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
			log(FINE, "CFP received from " + cfp.getSender().getName());

			ACLMessage propose = cfp.createReply();
			propose.setPerformative(ACLMessage.PROPOSE);
			propose.setLanguage(mCodec.getName());
			propose.setOntology(mOntology.getName());

			try {
				Action action = (Action) myAgent.getContentManager().extractContent(cfp);
				if (action.getAction() instanceof WantTo) {
					WantTo wantTo = (WantTo) action.getAction();

					// Print want to
					log(FINE, "Want to:" + //
					"\n\t Sell: " + wantTo.getWantToSell() + //
					"\n\t Metal: " + wantTo.getGood().getMetal().getMetalCode() + //
					"\n\t Quantity: " + wantTo.getGood().getQuantity());

					// TODO: TO BE IMPLEMENTED
					// Decide the bid
					double proposedBid = 0;
					if (wantTo.getGood().getMetal().equals(Metal.PLATINUM)) {
						proposedBid = mPlaPrice * (1 + BIDDING_PERCENT_BUY) * wantTo.getGood().getQuantity();
					} else if (wantTo.getGood().getMetal().equals(Metal.GOLD)) {
						proposedBid = mGolPrice * (1 + BIDDING_PERCENT_BUY) * wantTo.getGood().getQuantity();
					} else if (wantTo.getGood().getMetal().equals(Metal.SILVER)) {
						proposedBid = mSilPrice * (1 + BIDDING_PERCENT_BUY) * wantTo.getGood().getQuantity();
					} else {
						log(SEVERE, "Warning: Invalid metalcode");
					}

					if (proposedBid <= mMoney / 5) {
						log(FINE, "Proposing " + proposedBid + " to " + ((wantTo.getWantToSell()) ? "buy" : "sell") + ":" + // 
								"\n\t Metal: " + wantTo.getGood().getMetal().getMetalCode() + // 
								"\n\t Quantity: " + wantTo.getGood().getQuantity());
						Bid bid = new Bid(proposedBid);
						getContentManager().fillContent(propose, bid);
					} else {
						log(FINE, "Proposing " + proposedBid + " to " + ((wantTo.getWantToSell()) ? "buy" : "sell") + ":" + // 
								"\n\t Metal: " + wantTo.getGood().getMetal().getMetalCode() + // 
								"\n\t Quantity: " + wantTo.getGood().getQuantity());
						Bid bid = new Bid((wantTo.getWantToSell() ? 0: 1000000));
						getContentManager().fillContent(propose, bid);
					}
				} else {
					// Unexpected response received from the info agent.
					log(SEVERE, "Unexpected response from " + cfp.getSender().getName());
					throw new RefuseException("Unexpected response from " + cfp.getSender().getName());
				}
			} catch (Exception e) {
				log(e);
				// Unexpected response received from the info agent.
				log(SEVERE, "Unexpected response from " + cfp.getSender().getName());
				throw new RefuseException(e.getMessage());
			}
			return propose;

		}

		@Override
		protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
			log(FINE, "Proposal accepted");

			ACLMessage inform = accept.createReply();

			try {
				AbsPredicate absResult = (AbsPredicate) myAgent.getContentManager().extractAbsContent(accept);
				if (absResult.getTypeName().equals(BasicOntology.RESULT)) {
					// Check the deal
					Result result = (Result) mOntology.toObject(absResult);
					if (result.getValue() instanceof Deal) {
						Deal deal = (Deal) result.getValue();

						// Print agreed deal
						log(FINE, "Deal agreed:" + //
						"\n\t DealId: " + deal.getDealId() + //
						"\n\t Seller: " + deal.getSeller().getName() + //
						"\n\t Buyer: " + deal.getBuyer().getName() + //
						"\n\t Metal: " + deal.getGood().getMetal().getMetalCode() + //
						"\n\t Quantity: " + deal.getGood().getQuantity() + //
						"\n\t Value: " + deal.getValue());

						inform.setPerformative(ACLMessage.INFORM);

						// Send the deal to the bank agent
						addBehaviour(new RequestToCompleteADealBehaviour(myAgent, deal));

					} else {
						// Unexpected response received from the info agent.
						log(SEVERE, "Unexpected response from " + accept.getSender().getName());
						inform.setPerformative(ACLMessage.FAILURE);
						inform.setContent("Unexpected response from " + accept.getSender().getName());
					}
				} else {
					// Unexpected response received from the info agent.
					log(SEVERE, "Unexpected response from " + accept.getSender().getName());
					inform.setPerformative(ACLMessage.FAILURE);
					inform.setContent("Unexpected response from " + accept.getSender().getName());
				}
			} // End of try
			catch (Exception e) {
				log(e);
				inform.setPerformative(ACLMessage.FAILURE);
				inform.setContent(e.getMessage());
			}
			return inform;
		}

		@Override
		protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
			log(FINE, "Proposal rejected");
		}
	}
}
