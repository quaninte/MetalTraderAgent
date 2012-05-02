package agent.trader.user.cnpm1.nhom6;

import jade.content.abs.AbsPredicate;
import jade.content.lang.sl.SL0Vocabulary;
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

import java.util.Random;

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

public class Nakata extends MariaAgent {

	private static final Random RANDOM = new Random();

	// Current account
	public AssetBag bag;

	// Current Future Trend
	private FutureTrend mCurrentFutureTrend;

	// Last agreed deal in the market
	private Deal mLastAgreedDeal;

	public Nakata() {
		super(INFO);
	}

	@Override
	protected void setup() {
		super.setup();
		
		// create new Test
		Log.newTest();
		
		bag = new AssetBag(this);
		
		// add 3 assets
		bag.addAsset("gold", 0);
		bag.addAsset("plat", 0);
		bag.addAsset("silv", 0);

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

		// This behaviour will have a chance of 50% of sending out request to
		// buy or sell a random amount of random metal with threshold is set to
		// market price +/- 10% every 500 ms
		// Just here to show how a request could be sent to actively sell or buy
		// metal
		addBehaviour(new TickerBehaviour(this, 500) {

			@Override
			protected void onTick() {
				// Check the market price first
				addBehaviour(new RequestMarketPriceBehaviour(myAgent));
				if (RANDOM.nextInt(2) == 0) {
					// Wait 500 ms
					addBehaviour(new WakerBehaviour(myAgent, 500) {
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
								threshold = 100;
							} else if (mr > 0.66) {
								metal = Metal.GOLD;
								threshold = 100;
							} else {
								metal = Metal.SILVER;
								threshold = 100;
							}

							// How many unit (1 - 10)
							int quantity = RANDOM.nextInt(10) + 1;

							// Threshold value
							// -10% if it sell
							// +10% if it buy
							// * quantity to have the total value
							if (wantToSell)
								threshold = (threshold * (1 - RANDOM.nextDouble() * 0.1)) * quantity;
							else
								threshold = (threshold * (1 + RANDOM.nextDouble() * 0.1)) * quantity;

							AuctionRequest auction = new AuctionRequest(new AgentID(getAID().getName()), new WantTo(wantToSell, new Good(metal, quantity)), threshold);
							//addBehaviour(new RequestAuctionBehaviour(myAgent, auction));
						}
					});
				}
			}
		});

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
						bag.getAsset("plat").updatePrice(cmp.getPlatinumPrice());
						Log.updatePrice(bag.getAsset("plat").getType(), cmp.getPlatinumPrice());
						
						bag.getAsset("gold").updatePrice(cmp.getGoldPrice());
						Log.updatePrice(bag.getAsset("gold").getType(), cmp.getGoldPrice());
						
						bag.getAsset("silv").updatePrice(cmp.getSilverPrice());
						Log.updatePrice(bag.getAsset("silv").getType(), cmp.getSilverPrice());
						
					} else if (cs.getTypeName().equals(TradingOntology.FUTURE_TREND)) {
						// Update future trend
						FutureTrend trend = (FutureTrend) mOntology.toObject(cs);

						// Update new trend
						mCurrentFutureTrend = trend;

						log(FINE, "Future market Trend obtainted:\t" + //
						"\tMetal: " + trend.getMetal() + //
						"\tDirection: " + trend.getDirection());//
						
						String assetType = Asset.getAssetTypeFromMetalCode(trend.getMetal().getMetalCode());
						Asset asset = bag.getAsset(assetType);
						
						Log.addTrend(asset.getType(), trend.getDirection().toString());
						
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
	
	protected void updateBag(CurrentAccount account)
	{
		// Update current account
		bag.setBalance(account.getMoney());
		bag.getAsset("plat").setAmount(account.getPlatinumQuantity());
		bag.getAsset("gold").setAmount(account.getGoldQuantity());
		bag.getAsset("silv").setAmount(account.getSilverQuantity());
		
		// Print updated account
		slog("Current Account:" + // 
		"\n\t Money: " + account.getMoney() + // 
		"\n\t Platinum Quantity: " + account.getPlatinumQuantity() + // 
		"\n\t Gold Quantity: " + account.getGoldQuantity() + //
		"\n\t Silver Quantity: " + account.getSilverQuantity());//
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
					
					// update bag
					updateBag(account);
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
				if (absResult.getTypeName().equals(SL0Vocabulary.RESULT)) {
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

					// update bag
					updateBag(account);
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
				if (absResult.getTypeName().equals(SL0Vocabulary.RESULT)) {
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
			log(FINE, "Get last deal action has been a failure because: " + msg.getContent());
		}

		@Override
		protected void handleRefuse(ACLMessage msg) {
			log(FINE, "Get last deal action has been a rejected because: " + msg.getContent());
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
						proposedBid = 100 * (1 + (RANDOM.nextDouble() * 2 - 1) * 0.2) * wantTo.getGood().getQuantity();
					} else if (wantTo.getGood().getMetal().equals(Metal.GOLD)) {
						proposedBid = 100 * (1 + (RANDOM.nextDouble() * 2 - 1) * 0.2) * wantTo.getGood().getQuantity();
					} else if (wantTo.getGood().getMetal().equals(Metal.SILVER)) {
						proposedBid = 100 * (1 + (RANDOM.nextDouble() * 2 - 1) * 0.2) * wantTo.getGood().getQuantity();
					} else {
						log(SEVERE, "Warning: Invalid metalcode");
					}

					log(FINE, "Proposing " + proposedBid + " to " + ((wantTo.getWantToSell()) ? "buy" : "sell") + ":" + // 
					"\n\t Metal: " + wantTo.getGood().getMetal().getMetalCode() + // 
					"\n\t Quantity: " + wantTo.getGood().getQuantity());

					Bid bid = new Bid(proposedBid);
					getContentManager().fillContent(propose, bid);
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
				if (absResult.getTypeName().equals(SL0Vocabulary.RESULT)) {
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
