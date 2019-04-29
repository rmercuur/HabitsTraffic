
public class ContextElement {
	Location isLocatedAt;
	
	public void locate(Location isLocatedAt) {
		this.isLocatedAt = isLocatedAt;
		isLocatedAt.add(this);
	}
	
	public Location getLocation() {
		return isLocatedAt;
	}
}
