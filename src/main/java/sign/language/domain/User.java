package sign.language.domain;

import lombok.Getter;

@Getter
public class User {
    private String id;
    private String password;
    private String name;

    public User(String id, String password, String name) {
        this.id = id;
        this.password = password;
        this.name = name;
    }

}