
public class Activity extends ContextElement {
	String name;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Activity(String string) {
		setName(string);
	}
	
}
