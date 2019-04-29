import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;

public class Agent extends ContextElement {
	int ID;
	MyContextBuilder myContextBuilder;
	Table<ContextElement,Activity,Double> myHabitualTriggers;
	List<ContextElement> myPerformanceContext;
	Table<Activity,Value,Double> relatedValues;
	Map<Value,Double> myAdheredValues;
	List<Activity> myCandidates;
	String chosenMode;
	
	Home myHome;
	Work myWork;
	List<Resource> myResources;
	
	Activity chosenAction;
	
	static double habitThreshold = 0.5;
	double habitRate;
	

	public Agent(int agentID, MyContextBuilder myContextBuilder, Home familyHome, Work myWork, List<Resource> familyResources, List<Resource> personalResources) {
		this.ID = agentID;
		this.myContextBuilder = myContextBuilder;
		myCandidates= myContextBuilder.activities;
		
		
		relatedValues = HashBasedTable.create();
		for(Value V: myContextBuilder.values) {
			for(Activity AC: myContextBuilder.activities) {
				relatedValues.put(AC, V, normalRanged(0.5,0.1));
			}
		}
		
		myAdheredValues= new HashMap<Value,Double>();
		for(Value V: myContextBuilder.values) {
				myAdheredValues.put(V, normalRanged(1,0.5,0,2));
		}
			
		habitRate = getHabitRate(); //maybe this should be edited
		
		myPerformanceContext =new ArrayList<ContextElement>();
		myHome = familyHome;
		this.myWork = myWork;
		myResources =new ArrayList<Resource>();
		myResources.addAll(familyResources);
		myResources.addAll(personalResources);
	}
		
	//Creats Habitual Triggers After All Context Elements Have Been Added To the Simulation
	public void createHabitualTriggers() {
		myHabitualTriggers = HashBasedTable.create();
		
		for(ContextElement CE: myContextBuilder.contextElements) {
			for(Activity AC: myContextBuilder.activities) {
				myHabitualTriggers.put(CE, AC, 0.0);
			}
		}
	}
	

	@ScheduledMethod(start = 1, interval = 1, priority = 10)
	public void sense() {
		myPerformanceContext.clear();
		myPerformanceContext.addAll(isLocatedAt.getLocatedHere());
	}
	
	@ScheduledMethod(start = 1, interval = 1, priority = 9)
	public void decide() {
		double attention = getAttention();
		List<Activity> currentCandidates =new ArrayList<Activity>();
		for(Activity Ac: myCandidates) {
			double habitStrength = calculateHabitStrength(Ac);
			if (habitStrength > attention * habitThreshold) {
				currentCandidates.add(Ac);
			}
		}
		
		if (currentCandidates.size() ==0) {
			chosenAction = intentionalDecision(myCandidates);
			chosenMode = "Intentional0";
		}
		if (currentCandidates.size() ==1) {
			chosenAction = currentCandidates.get(0);
			chosenMode = "Habitual";
		}
		if (currentCandidates.size() > 1) {
			if (currentCandidates.size() == myCandidates.size()) { //the case where they are all habitual somehow;
				chosenAction = intentionalDecision(currentCandidates);
				chosenMode = "IntentionalAll";
			}
			else {
				chosenAction = intentionalDecision(currentCandidates);
				chosenMode = "IntentionalFiltered";
			}
		}

	}

	private double getAttention() {
		double timeAfterIntervention =
				RunEnvironment.getInstance().getCurrentSchedule().getTickCount()-
				RunEnvironment.getInstance().getParameters().getInteger("InterventionTime");
		double lessenAttention = Math.pow(0.995, timeAfterIntervention);		
		double multiplier = 
				RunEnvironment.getInstance().getCurrentSchedule().getTickCount() > 
					RunEnvironment.getInstance().getParameters().getInteger("InterventionTime") 
		
				? Doubles.constrainToRange(
						RunEnvironment.getInstance().getParameters().getDouble("getExtraAttention")
						*lessenAttention,1,10)
						:1;
		return multiplier*normalRanged(1.0,0.3);
	}
	
	private double getHabitRate() {
		double habitRate = 0;
		while(habitRate == 0|| habitRate == 1){
        	habitRate = normalRanged(0.03,0.5);
        } 
		return habitRate;
	}
	
	private Activity intentionalDecision(List<Activity> currentCandidates) {
		return currentCandidates.stream().
				max(Comparator.comparing(candidate -> candidateRating(candidate))).get();
	}
	
	private double candidateRating(Activity candidate) {
		return myContextBuilder.values.stream().
				mapToDouble(value -> candidateValueRating(candidate, value)).	
				sum();
	}
	
	private double candidateValueRating(Activity candidate, Value value) {
		double multiplierWalk = 
				RunEnvironment.getInstance().getCurrentSchedule().getTickCount() > 
					RunEnvironment.getInstance().getParameters().getInteger("InterventionTime") 
				&& "walk".equals(candidate.getName()) 
				? 2:1;
		double multiplierBike = 
							RunEnvironment.getInstance().getCurrentSchedule().getTickCount() > 
								RunEnvironment.getInstance().getParameters().getInteger("InterventionTime") 
							&& "rideBike".equals(candidate.getName()) 
							&& RunEnvironment.getInstance().getParameters().getBoolean("motivateMultipleAlternatives")
							? 2:1;

		double multiplierCar = 
			"takeCar".equals(candidate.getName())  
			? 1.5:1;
		return multiplierCar* multiplierWalk* multiplierBike* relatedValues.get(candidate, value) * myAdheredValues.get(value);
	}
	
	private double calculateHabitStrength(Activity AC) {
		return myPerformanceContext.stream().
				mapToDouble(i -> myHabitualTriggers.get(i,AC)).average().getAsDouble();
	}

	@ScheduledMethod(start = 1, interval = 1, priority = 8)
	public void act() {
		
	}
	
	@ScheduledMethod(start = 1, interval = 1, priority = 7)
	public void update() {
		for(ContextElement CE:myPerformanceContext) {
			for(Activity AC:myCandidates) {
				double oldHabitStrength = myHabitualTriggers.get(CE, AC);
				double newHabitStrength = 0;
				if (AC == chosenAction) {
					newHabitStrength = ((1-habitRate) *oldHabitStrength) + (habitRate *1);
				} else {
					newHabitStrength =  RunEnvironment.getInstance().getParameters().getBoolean("habitsDecrease") ?
							((1-habitRate) * oldHabitStrength) + (habitRate * 0):
								oldHabitStrength;
				}
				myHabitualTriggers.put(CE, AC, newHabitStrength);
			}
		}
	}
	
	
	public double normalRanged(double mean, double sd) {
		return Doubles.constrainToRange(RandomHelper.createNormal(mean, sd).nextDouble(), 0, 1);
	}
	
	public double normalRanged(double mean, double sd, double min, double max) {
		return Doubles.constrainToRange(RandomHelper.createNormal(mean, sd).nextDouble(), min, max);
	}
	
	/*Data-analysis*/
	
	public String getChosenAction() {
		return chosenAction.name;
	}
	public int getID() {
		return ID;
	}
	
	public double getWalkHabitStrength() {
		Activity activityToReport = myCandidates.stream()
				  .filter(candidate -> "walk".equals(candidate.getName()))
				  .findAny()
				  .orElse(null);

		return calculateHabitStrength(activityToReport);
	}
	
	public double getRideBikeHabitStrength() {
		Activity activityToReport = myCandidates.stream()
				  .filter(candidate -> "rideBike".equals(candidate.getName()))
				  .findAny()
				  .orElse(null);

		return calculateHabitStrength(activityToReport);
	}
	
	public double getTakeCarHabitStrength() {
		Activity activityToReport = myCandidates.stream()
				  .filter(candidate -> "takeCar".equals(candidate.getName()))
				  .findAny()
				  .orElse(null);

		return calculateHabitStrength(activityToReport);
	}
	
	public double getWalkIntentionStrength() {
		Activity activityToReport = myCandidates.stream()
				  .filter(candidate -> "walk".equals(candidate.getName()))
				  .findAny()
				  .orElse(null);

		return candidateRating(activityToReport);
	}
	
	public double getRideBikeIntentionStrength() {
		Activity activityToReport = myCandidates.stream()
				  .filter(candidate -> "rideBike".equals(candidate.getName()))
				  .findAny()
				  .orElse(null);

		return candidateRating(activityToReport);
	}
	
	public double getTakeCarIntentionStrength() {
		Activity activityToReport = myCandidates.stream()
				  .filter(candidate -> "takeCar".equals(candidate.getName()))
				  .findAny()
				  .orElse(null);

		return candidateRating(activityToReport);
	}
	
	public String getChosenMode() {
		return chosenMode;
	}
}
