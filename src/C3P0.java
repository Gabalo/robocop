package groupx;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.User;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

/**
 * A simple example agent that makes random bids above a minimum target utility. 
 *
 * @author Tim Baarslag
 */
public class C3P0 extends AbstractNegotiationParty 
{
	private static double MINIMUM_TARGET = 0.8;
	private Bid lastOffer;
	// C3P0 START
	private List<List<Integer>> FreqTable1 = new ArrayList<List<Integer>>();
	private List<List<Integer>> FreqTable2 = new ArrayList<List<Integer>>();
	private List<List<Double>> CalcTable = new ArrayList<List<Double>>();
	private List<Double> Weights = new ArrayList<Double>();
	private List<Double> NormWeights = new ArrayList<Double>();
	private List<Integer> ValueIndex = new ArrayList<Integer>();
	private int NoOfBids;
	// C3P0 END
	
	/**
	 * Initializes a new instance of the agent.
	 */
	@Override
	public void init(NegotiationInfo info) 
	{
		super.init(info);
		
		if (hasPreferenceUncertainty()) {
			System.out.println("Preference uncertainty is enabled.");
			System.out.println("Agent ID: " + info.getAgentID().toString());
			System.out.println("No. of possible bids: " + userModel.getDomain().getNumberOfPossibleBids());
			System.out.println("No. of bids in the preference ranking: " + userModel.getBidRanking().getSize());
			System.out.println("Elicitation Cost: " + info.getUser().getElicitationCost());
			System.out.println("Bid with lowest utility: " + userModel.getBidRanking().getMinimalBid());
			System.out.println("Bid with highest utility: " + userModel.getBidRanking().getMaximalBid());
			System.out.println("5th bid in the ranking list: " + userModel.getBidRanking().getBidOrder().get(5));
		}
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

		List< Issue > issues = additiveUtilitySpace.getDomain().getIssues();
		//Bid TestBid = null;
		
		for (Issue issue : issues) {
			
			// C3P0 START - For each issue create a new row in the frequency table
			List<Integer> issuerow1 = new ArrayList<Integer>();
			List<Integer> issuerow2 = new ArrayList<Integer>();
			List<Double> issuerow3 = new ArrayList<Double>();
			// C3P0 END
			
		    int issueNumber = issue.getNumber();
		    System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

		    // Assuming that issues are discrete only
		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	
		    	// C3P0 START - For each discrete value add a new column in the issue row
		    	issuerow1.add(0);
		    	issuerow2.add(0);
		    	issuerow3.add(0.0);
		    	// C3P0 END
		    	
		    	System.out.println(valueDiscrete.getValue());
		        System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
		        try {
		            System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
		        } catch (Exception e) {
		            e.printStackTrace();
		        } 
		    }
		    
		    // C3P0 START - Add the issue row to the frequency table
		    FreqTable1.add(issuerow1);
		    FreqTable2.add(issuerow2);
		    CalcTable.add(issuerow3);
		    Weights.add(0.0);
		    NormWeights.add(0.0);
		    ValueIndex.add(0);
		    NoOfBids = 0;
		    // C3P0 END
		}	
	}

	
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) 
	{
		// C3P0 START - Check for acceptance if we have received an offer
		double threshold = (getUtility(getMaxUtilityBid()) + getUtility(getMinUtilityBid()))/2;
		if (lastOffer != null) {
			if (getUtility(lastOffer) >= getUtility(getMaxUtilityBid())) {
				System.out.println("Accepted bid equal or above MaxUtility bid: " + getUtility(getMaxUtilityBid()));
				return new Accept(getPartyId(), lastOffer);}
			if (timeline.getTime() >= 0.5) {
				if (getUtility(lastOffer) >= utilitySpace.getReservationValue()) {
					if (getUtility(lastOffer) >= threshold ) {
						System.out.println("Accepted bid above threshold: " + threshold + " at time: " + timeline.getTime() );
						return new Accept(getPartyId(), lastOffer);}		// G: Accept offer above thresh. during the final half of timeline
				} 
				else {	// G: If the offer is lower than the Reserv. Value then End Negotiation
					System.out.println("C3P0 ended negotiation at time: " + timeline.getTime() + ", bid lower than reserv. value: " + utilitySpace.getReservationValue());
					return new EndNegotiation(getPartyId());
				}
			}
			if (timeline.getTime() >= 0.95)
				if (getUtility(lastOffer) >= utilitySpace.getReservationValue()) {
					System.out.println("Accepted (any) bid with terrible utility: " + getUtility(lastOffer));
					return new Accept(getPartyId(), lastOffer);} // G: Accept any offer at the end of the timeline
				else {	// G: If the offer is lower than the Reserv. Value then End Negotiation
					System.out.println("C3P0 ended negotiation at time: " + timeline.getTime() + ", bid lower than reserv. value: " + utilitySpace.getReservationValue());
					return new EndNegotiation(getPartyId());
				}
		}
		// Otherwise, send out a random offer above the target utility 
		List<Bid> rBids = generateRandomBids();
		double OpponentUtility;
		double WinningOpUtility = 0.0;
		Bid WinningBid = generateRandomBidAboveTarget();
		
		for (Bid thisBid : rBids) {
			OpponentUtility = getOpponentUtility(thisBid);
			if (OpponentUtility > WinningOpUtility) {
				WinningOpUtility = OpponentUtility;
				WinningBid = thisBid;
				//System.out.println("New winning utility!: " + WinningOpUtility);
			}
		}
		//System.out.println("rBids array size: " + rBids.size());
		// C3P0 END
		return new Offer(getPartyId(), WinningBid);
	}
	
	private Bid getMaxUtilityBid() {
	    try {
	        return utilitySpace.getMaxUtilityBid();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	private Bid getMinUtilityBid() {
	    try {
	        return utilitySpace.getMinUtilityBid();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	private Bid generateRandomBidAboveTarget() 
	{
		Bid randomBid;
		double util;
		int i = 0;
		// try 100 times to find a bid under the target utility
		do 
		{	
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
		} 
		while (util < MINIMUM_TARGET && i++ < 100);		
		return randomBid;
	}

	// C3P0 START - Instead of generating a single random bid, generate an array of them
	private List<Bid> generateRandomBids() 
	{
		List<Bid> rBids = new ArrayList<Bid>();
		Bid randomBid;
		
		double util;
		int i = 0;
		// try 100 times to find a bid under the target utility
		do 
		{	
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
			if (util > MINIMUM_TARGET) {
				rBids.add(randomBid);
			}
		} 
		while (i++ < 1000);		
		return rBids;
	}
	// C3P0 END
	
	/**
	 * Remembers the offers received by the opponent.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action) 
	{
		// C3P0 START
		double utility = 0.0;
		// C3P0 END
		if (action instanceof Offer) 
		{
			lastOffer = ((Offer) action).getBid();
			
			// C3P0 START
			NoOfBids = NoOfBids + 1;
			updateFreqTable(lastOffer);
			updateWeightsArray(lastOffer);
			utility = getOpponentUtility(lastOffer);
			// C3P0 END
		}
		
		// C3P0 START -- DEBUG: Print the freq/calc/weights tables to check they are correctly updated
		System.out.println("Frequency Table");
		for (int i=0; i<FreqTable1.size();i++)
	        System.out.println(FreqTable1.get(i));
		
		System.out.println("Calculation Table");
		for (int i=0; i<CalcTable.size();i++)
	        System.out.println(CalcTable.get(i));
		
		System.out.println("Normalized Weights Array");
		for (int i=0; i<NormWeights.size();i++)
	        System.out.println(NormWeights.get(i));
		
		System.out.println("Valuation for this offer: " + utility);
		
		System.out.println("-----------------------------");
		// C3P0 END
	}
	
	@Override
	public String getDescription() 
	{
		return "I am your father.";
	}

	/**
	 * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behavior.
	 */
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() 
	{
		return super.estimateUtilitySpace();
	}

	// C3P0 START -- New functions to update freq/calc/weights tables 
	
	private void updateFreqTable(Bid offer) {
		
		int intValue;
		double NumberOfValues;
		double n0;
		
		List< Issue > bidissues = offer.getIssues();
		
		for (Issue issue : bidissues) {
			
			// Iterate over each issue
		    int issueNumber = issue.getNumber();
		    // Issue number starts at 1 but array index starts at 0, hence the -1. 
		    issueNumber = issueNumber - 1;
		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    
		    int valueNumber = 0;
		    // Iterate over each value of the issue
		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	// If the specific value for the specific issue was offered, then add 1 to that cell
		    	if (offer.getValue(issueNumber+1).equals(valueDiscrete)) {
		    		intValue = FreqTable1.get(issueNumber).get(valueNumber);
		    		intValue = intValue + 1;			    		
		    		FreqTable1.get(issueNumber).set(valueNumber, intValue);
		    	}
		    	FreqTable2.get(issueNumber).set(valueNumber, FreqTable1.get(issueNumber).get(valueNumber));
		    	valueNumber = valueNumber + 1;
		    }   
		    FreqTable2.get(issueNumber).sort(null);
		    NumberOfValues = (double)valueNumber;
		    
		    valueNumber = 0;
		    for (int valueD : FreqTable1.get(issueNumber)) {		    	
		    	n0 = (double)FreqTable2.get(issueNumber).lastIndexOf(valueD) + 1;	
		    	CalcTable.get(issueNumber).set(valueNumber, (n0 / NumberOfValues));
		    	valueNumber = valueNumber + 1;   	
		    }
		}
	}
	
	private void updateWeightsArray(Bid offer) {
		
		double weight;
		double totalWeight;
		
		List< Issue > bidissues = offer.getIssues();
		weight = 0;
		totalWeight = 0;
		
		for (Issue issue : bidissues) {
			
			// Iterate over each issue
		    int issueNumber = issue.getNumber();
		    // Issue number starts at 1 but array index starts at 0, hence the -1. 
		    issueNumber = issueNumber - 1;
		    
		    Weights.set(issueNumber, 0.0);
		    for (int valueD : FreqTable1.get(issueNumber)) {
		    	weight = (Math.pow(valueD, 2) / Math.pow(NoOfBids, 2)) + Weights.get(issueNumber);
		    	Weights.set(issueNumber, weight);
		    }
		    totalWeight = totalWeight + weight;
		    
		}
		int issueNumber = 0;
		for (double w : Weights) {
			NormWeights.set(issueNumber, w/totalWeight);
			issueNumber = issueNumber + 1;
		}
	}
	
	private double getOpponentUtility(Bid offer) {
		
		int issueIndex = 0;
		double valuation = 0;
		
		List< Issue > bidissues = offer.getIssues();
		
		for (Issue issue : bidissues) {
			int issueNumber = issue.getNumber();
		    issueNumber = issueNumber - 1;
		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    
		    int valueNumber = 0;
		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	if (offer.getValue(issueNumber+1).equals(valueDiscrete)) {
		    		ValueIndex.set(issueNumber, valueNumber);
	    		}
		    	valueNumber = valueNumber + 1;
		    }
		}
		
		for (double w: NormWeights) {
			double temp = CalcTable.get(issueIndex).get(ValueIndex.get(issueIndex));
			valuation = valuation + temp * w;
			issueIndex = issueIndex + 1;
		}
		return valuation;
	}
	
	// C3P0 END
	

}
