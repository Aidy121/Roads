// Author: Aidan Fisher

import java.awt.*;

public class ExtendedStroke implements Stroke {

	private Stroke stroke = null;
	private Polygon2D polygon = null;
	private int strokeExtension;

	public ExtendedStroke(Stroke stroke, int strokeExtension) {
		this.stroke = stroke;
		this.strokeExtension = strokeExtension;
	}

	public ExtendedStroke(Polygon2D polygon, int strokeExtension) {
		this.polygon = polygon;
		this.strokeExtension = strokeExtension;
	}

	@Override
	public Shape createStrokedShape(Shape shape) {
		BasicStroke basicStroke = new BasicStroke((float) (strokeExtension * Component.zoomLevel));
		if (stroke != null) {
			return basicStroke.createStrokedShape(stroke.createStrokedShape(shape));
		} else {
			return basicStroke.createStrokedShape(polygon);
		}
	}
}
