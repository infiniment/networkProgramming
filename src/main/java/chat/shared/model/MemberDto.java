package chat.shared.model;

public class MemberDto {
    private final long id;
    private final String name;

    public MemberDto(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId()   { return id; }
    public String getName() { return name; }
}
