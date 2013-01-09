package IC.Symbols;

public class ArraySymbolType extends SymbolType {

	SymbolType baseType;

	// int dimension;

	public ArraySymbolType(SymbolType baseType) {
		this.baseType = baseType;
		// this.dimension = dimension;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(baseType);
		// for (int i = 0; i < dimension; ++i) {
		builder.append("[]");
		// }
		return builder.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ArraySymbolType other = (ArraySymbolType) obj;
		if (baseType == null) {
			if (other.baseType != null)
				return false;
		} else if (!baseType.equals(other.baseType))
			return false;
		// if (dimension != other.dimension)
		// return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((baseType == null) ? 0 : baseType.hashCode());
		// result = prime * result + dimension;
		return result;
	}

	@Override
	public String getHeader() {
		return "Array type";
	}

	@Override
	public int getDisplaySortIndex() {
		return 3;
	}

}